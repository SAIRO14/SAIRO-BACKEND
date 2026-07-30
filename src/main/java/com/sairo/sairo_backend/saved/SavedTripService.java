package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.common.BusinessException;
import com.sairo.sairo_backend.common.ErrorCode;
import com.sairo.sairo_backend.course.CourseRepository;
import com.sairo.sairo_backend.course.CourseSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class SavedTripService {

    private final SavedTripRepository savedTripRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;

    /**
     * 코스를 저장 여행지로 담는다.
     *
     * <p>저장할 내용은 서버가 저장해둔 코스에서만 가져온다. 요청은 {@code courseId}만 받는다.
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
        String snapshotJson = courseRepository.findCourseDataById(request.courseId())
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
                CourseFingerprint.of(snapshot)
        );

        return SavedTripResponse.from(saved);
    }
}
