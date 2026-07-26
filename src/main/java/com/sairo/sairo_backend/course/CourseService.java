package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.common.BusinessException;
import com.sairo.sairo_backend.common.ErrorCode;
import com.sairo.sairo_backend.spot.Spot;
import com.sairo.sairo_backend.spot.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final SpotRepository spotRepository;
    private final CourseRepository courseRepository;
    private final SharedCourseRepository sharedCourseRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.share-base-url:https://sairo.app/shared}")
    private String shareBaseUrl;

    /**
     * 코스를 만들어 저장하고 courseId를 발급한다.
     *
     * <p>저장하지 않으면 공유와 저장이 코스를 ID로 참조할 수 없다.
     * 스냅샷에 지역을 함께 담으므로 요청 지역이 실제 장소의 지역과 같은지 먼저 검증한다.
     */
    public CourseResponse buildCourse(CourseRequest request) {
        List<Spot> spots = spotRepository.findAllById(request.spotIds());
        if (spots.size() < 2) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_SPOTS, "유효한 장소가 2개 이상 필요합니다.");
        }
        verifyRegion(request.regionName(), spots);

        List<Spot> sorted = sortByNearestNeighbor(spots);

        int mid = sorted.size() / 2;
        List<SpotSummary> day1 = sorted.subList(0, mid).stream().map(SpotSummary::from).collect(Collectors.toList());
        List<SpotSummary> day2 = sorted.subList(mid, sorted.size()).stream().map(SpotSummary::from).collect(Collectors.toList());

        String courseId = UUID.randomUUID().toString();
        courseRepository.save(courseId, objectMapper.writeValueAsString(
                new CourseSnapshot(request.regionName(), day1, day2)));

        return new CourseResponse(courseId, day1, day2);
    }

    /**
     * 요청 지역에 속하지 않는 장소가 섞였는지 본다. 섞이면 코스도 스냅샷도 틀린 지역을 갖게 된다.
     *
     * <p>판정은 장소를 고를 때 쓰는 규칙과 같아야 한다. `SpotRepository.findByRegionContaining`이
     * `ILIKE '%지역%'` 부분 일치로 찾으므로 여기서도 대소문자 무시 부분 일치로 본다.
     * 완전 일치로 보면 `"제주"`로 조회된 `"제주도"` 장소가 검증에서 막혀,
     * 추천에서 코스 생성으로 이어지는 정상 흐름이 끊긴다.
     */
    private void verifyRegion(String regionName, List<Spot> spots) {
        String needle = regionName.toLowerCase(Locale.ROOT);
        boolean allMatch = spots.stream().allMatch(s -> s.getRegionName() != null
                && s.getRegionName().toLowerCase(Locale.ROOT).contains(needle));
        if (!allMatch) {
            throw new BusinessException(ErrorCode.COURSE_REGION_MISMATCH,
                    "요청한 지역에 속하지 않는 장소가 있습니다.");
        }
    }

    /**
     * 서버가 저장해둔 코스를 읽어 공유 스냅샷을 만든다.
     *
     * <p>요청 본문을 받지 않는다. 클라이언트가 보낸 코스를 그대로 저장하면
     * 서버가 만들지 않은 코스로도 공유 링크를 발급할 수 있다.
     *
     * <p>같은 코스를 여러 번 공유해도 링크는 하나다 (리포지토리에서 처리).
     */
    public ShareCourseResponse shareCourse(String courseId) {
        String snapshotJson = courseRepository.findCourseDataById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        String shareId;
        try {
            shareId = sharedCourseRepository.save(courseId, snapshotJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SHARE_CREATION_FAILED, "공유 코스 저장 실패", e);
        }

        return new ShareCourseResponse(shareId, shareBaseUrl + "/" + shareId);
    }

    public SharedCourseViewResponse getSharedCourse(String shareId) {
        String json = sharedCourseRepository.findCourseDataById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARED_COURSE_NOT_FOUND));

        try {
            CourseSnapshot snapshot = objectMapper.readValue(json, CourseSnapshot.class);
            return new SharedCourseViewResponse(shareId, snapshot.regionName(), snapshot.day1(), snapshot.day2());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "코스 데이터 역직렬화 실패", e);
        }
    }

    // ─── 좌표 기반 Greedy Nearest-Neighbor 정렬 ──────────────────────────────
    //
    // 같은 요청에는 항상 같은 코스가 나와야 한다. spotRepository.findAllById()는
    // 반환 순서를 보장하지 않으므로, 목록 순서에 의존하는 지점을 모두 없앤다.
    //   - 시작점: 가장 북쪽 장소 (동일 위도면 spotId가 작은 쪽)
    //   - 다음 장소: 최단 거리 (거리가 같으면 spotId가 작은 쪽)
    //   - 좌표 없는 장소: spotId 순으로 뒤에 배치
    private List<Spot> sortByNearestNeighbor(List<Spot> spots) {
        List<Spot> withCoords = spots.stream()
                .filter(s -> s.getLat() != null && s.getLng() != null)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Spot> noCoords = spots.stream()
                .filter(s -> s.getLat() == null || s.getLng() == null)
                .sorted(Comparator.comparing(Spot::getSpotId))
                .collect(Collectors.toList());

        if (withCoords.isEmpty()) {
            return noCoords.isEmpty() ? spots : noCoords;
        }

        List<Spot> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        Spot current = withCoords.stream()
                .min(Comparator.comparingDouble(Spot::getLat).reversed()
                        .thenComparing(Spot::getSpotId))
                .orElseThrow();
        sorted.add(current);
        visited.add(current.getSpotId());

        while (sorted.size() < withCoords.size()) {
            Spot finalCurrent = current;
            Spot next = withCoords.stream()
                    .filter(s -> !visited.contains(s.getSpotId()))
                    .min(Comparator.comparingDouble((Spot s) -> euclidean(finalCurrent, s))
                            .thenComparing(Spot::getSpotId))
                    .orElseThrow();
            sorted.add(next);
            visited.add(next.getSpotId());
            current = next;
        }

        sorted.addAll(noCoords);
        return sorted;
    }

    private double euclidean(Spot a, Spot b) {
        double dlat = a.getLat() - b.getLat();
        double dlng = a.getLng() - b.getLng();
        return dlat * dlat + dlng * dlng;
    }
}
