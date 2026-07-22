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
    private final SharedCourseRepository sharedCourseRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.share-base-url:https://sairo.app/shared}")
    private String shareBaseUrl;

    public CourseResponse buildCourse(CourseRequest request) {
        List<Spot> spots = spotRepository.findAllById(request.spotIds());
        if (spots.size() < 2) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_SPOTS, "유효한 장소가 2개 이상 필요합니다.");
        }

        List<Spot> sorted = sortByNearestNeighbor(spots);

        int mid = sorted.size() / 2;
        List<SpotSummary> day1 = sorted.subList(0, mid).stream().map(SpotSummary::from).collect(Collectors.toList());
        List<SpotSummary> day2 = sorted.subList(mid, sorted.size()).stream().map(SpotSummary::from).collect(Collectors.toList());

        return new CourseResponse(UUID.randomUUID().toString(), day1, day2);
    }

    // 직렬화와 저장 모두 공유 생성 실패다. 저장을 try 밖에 두면 DB 장애가
    // INTERNAL_ERROR로 나가 Swagger에 문서화한 SHARE_CREATION_FAILED와 어긋난다.
    public ShareCourseResponse shareCourse(ShareCourseRequest request) {
        String shareId;
        try {
            String courseDataJson = objectMapper.writeValueAsString(request);
            shareId = sharedCourseRepository.save(courseDataJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SHARE_CREATION_FAILED, "공유 코스 저장 실패", e);
        }

        return new ShareCourseResponse(shareId, shareBaseUrl + "/" + shareId);
    }

    public SharedCourseViewResponse getSharedCourse(String shareId) {
        String json = sharedCourseRepository.findCourseDataById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARED_COURSE_NOT_FOUND));

        try {
            ShareCourseRequest data = objectMapper.readValue(json, ShareCourseRequest.class);
            return new SharedCourseViewResponse(shareId, data.day1(), data.day2());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "코스 데이터 역직렬화 실패", e);
        }
    }

    // ─── 좌표 기반 Greedy Nearest-Neighbor 정렬 ──────────────────────────────
    // 좌표 없는 spot은 후순위 처리 (null 좌표 → 뒤로 배치)
    private List<Spot> sortByNearestNeighbor(List<Spot> spots) {
        List<Spot> withCoords = spots.stream()
                .filter(s -> s.getLat() != null && s.getLng() != null)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Spot> noCoords = spots.stream()
                .filter(s -> s.getLat() == null || s.getLng() == null)
                .collect(Collectors.toList());

        if (withCoords.isEmpty()) return spots;

        List<Spot> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        Spot current = withCoords.get(0);
        sorted.add(current);
        visited.add(current.getSpotId());

        while (sorted.size() < withCoords.size()) {
            Spot finalCurrent = current;
            Spot next = withCoords.stream()
                    .filter(s -> !visited.contains(s.getSpotId()))
                    .min(Comparator.comparingDouble(s -> euclidean(finalCurrent, s)))
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
