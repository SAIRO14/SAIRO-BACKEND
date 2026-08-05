package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.common.BusinessException;
import com.sairo.sairo_backend.common.ErrorCode;
import com.sairo.sairo_backend.course.CourseRepository;
import com.sairo.sairo_backend.course.CourseSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedTripService {

    // CourseRepository만 의존하고 CourseService는 보지 않는다.
    // CourseService → SavedTripService(isSaved)가 이미 있어, CourseService를 여기서 주입하면
    // 빈 사이클로 기동이 실패한다.
    private final SavedTripRepository savedTripRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;

    /**
     * 코스를 저장 여행지로 담는다.
     *
     * <p>저장할 내용은 서버가 저장해둔 코스에서만 가져온다. 요청은 {@code courseId}만 받는다.
     *
     * <p><b>자기가 만든 코스만 저장할 수 있다.</b> 남의 코스는 없는 것과 같게 404다. (ADR 0012)
     *
     * <p>중복 요청에 안전하다. 같은 사용자가 같은 지역의 같은 장소 구성을 다시 저장하면
     * 새 항목을 만들지 않고 기존 항목을 그대로 돌려준다.
     * 판정 키는 {@code (device_id, 코스 지문)}이다. (ADR 0011)
     *
     * <p>지역명은 판정에 넣지 않는다. 한 코스의 장소는 모두 같은 지역이어야 하므로
     * 장소 구성이 정해지면 지역명도 함께 정해진다.
     *
     * <p>키가 같아도 스냅샷이 완전히 같다는 뜻은 아니다. 장소 마스터가 바뀐 뒤 만든 코스는
     * 이름·좌표가 달라도 같은 항목으로 본다. 이때 남는 것은 <b>최초 저장 시점의 코스</b>다.
     */
    SavedTripResponse save(String deviceId, SavedTripRequest request) {
        String snapshotJson = courseRepository.findCourseDataByIdAndDeviceId(request.courseId(), deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        CourseSnapshot snapshot;
        try {
            snapshot = objectMapper.readValue(snapshotJson, CourseSnapshot.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "코스 데이터 역직렬화 실패", e);
        }

        SavedTrip saved = savedTripRepository.save(
                UUID.randomUUID().toString(),
                deviceId,
                request.courseId(),
                snapshot.regionName(),
                CourseFingerprint.of(snapshot),
                snapshot.regionArea(),
                snapshot.imageUrl(),
                snapshot.reason()
        );

        return SavedTripResponse.from(saved);
    }

    /**
     * 저장 목록을 최근 저장순 한 페이지로 읽는다. (#31)
     *
     * <p>소유자 조건은 리포지토리 쿼리에 있다. 여기서 다시 거르지 않는다.
     *
     * <p>다음 페이지가 있는지 알기 위해 {@code size + 1}개를 읽는다. 별도 {@code COUNT} 쿼리를
     * 쓰지 않는 이유는, 그 사이에 항목이 늘거나 줄면 개수와 실제 페이지가 어긋나기 때문이다.
     * 더 읽힌 한 행은 응답에서 잘라내고 "다음 페이지 있음"의 근거로만 쓴다.
     *
     * <p>목록은 {@code saved_trips} 행만 읽고 코스 스냅샷은 건드리지 않는다. 코스 내용은
     * 항목을 눌렀을 때 코스 조회로 가져간다. 그래서 이 경로에는 역직렬화가 없고,
     * 항목 하나가 깨져 목록 전체가 실패하는 경우도 없다. ({@code docs/api-contract.md} §6)
     */
    SavedTripListResponse findPage(String deviceId, String encodedCursor, int size) {
        // 비어 있는 커서는 없는 것과 같게 다룬다. 클라이언트가 null 커서를 빈 문자열로
        // 직렬화하는 일이 흔한데, 그때 첫 페이지 요청이 400이 되면 목록을 시작할 수 없다.
        // 디바이스 헤더와 같은 판단이다. (docs/api-contract.md §4)
        // §5가 막으려는 "조용히 첫 페이지 주기"에는 해당하지 않는다. 커서를 준 적이 없는 요청이다.
        boolean firstPage = encodedCursor == null || encodedCursor.isBlank();
        SavedTripCursor cursor = firstPage ? null : SavedTripCursor.decode(encodedCursor);

        List<SavedTrip> rows = savedTripRepository.findPage(deviceId, cursor, size + 1);

        boolean hasNext = rows.size() > size;
        List<SavedTrip> page = hasNext ? rows.subList(0, size) : rows;

        return new SavedTripListResponse(
                page.stream().map(SavedTripResponse::from).toList(),
                // hasNext가 참이면 size + 1개를 읽었다는 뜻이라 page는 비어 있지 않다.
                hasNext ? SavedTripCursor.from(page.get(page.size() - 1)).encode() : null
        );
    }

    /**
     * 저장을 해제한다. (#32)
     *
     * <p>소유자 조건은 리포지토리 쿼리에 있다. 여기서 먼저 읽어 확인하지 않는다.
     *
     * <p><b>지울 것이 없어도 성공이다.</b> 세 경우가 모두 같은 응답으로 나간다.
     * 내 항목을 지웠을 때, 이미 지워진 항목을 다시 지웠을 때, 남의 항목을 지우려 했을 때다.
     *
     * <p>앞의 둘은 멱등성 때문이다. 네트워크 재시도나 연속 탭으로 같은 요청이 두 번 도착하는데,
     * 두 번째가 실패로 나가면 클라이언트는 지워지지 않았다고 판단한다.
     * ({@code docs/api-contract.md} §4)
     *
     * <p>남의 항목까지 여기 묶은 것은 <b>존재를 감추기 위해서다.</b> 남의 항목만 404로 답하면
     * 없는 ID는 성공, 있는 ID는 404가 되어 ID를 바꿔가며 실재 여부를 알아낼 수 있다.
     * 403 대신 404를 쓰기로 한 이유가 그대로 무너지므로, 응답으로 구분하지 않는다.
     * 그래서 이 경로는 {@code SAVED_TRIP_NOT_FOUND}도 {@code SAVED_TRIP_FORBIDDEN}도 던지지 않는다.
     */
    void delete(String deviceId, String savedTripId) {
        savedTripRepository.deleteByIdAndDeviceId(savedTripId, deviceId);
    }

    /**
     * 이 기기가 해당 스냅샷과 같은 장소 구성의 코스를 저장한 적 있는지 반환한다.
     *
     * <p>판정 키는 {@code courseId}가 아니라 코스 지문이다. 같은 장소로 만든 코스는
     * {@code courseId}가 달라도 같은 저장 항목으로 본다. (ADR 0011)
     */
    public boolean isSaved(String deviceId, CourseSnapshot snapshot) {
        return savedTripRepository.existsByDeviceIdAndFingerprint(
                deviceId, CourseFingerprint.of(snapshot));
    }
}
