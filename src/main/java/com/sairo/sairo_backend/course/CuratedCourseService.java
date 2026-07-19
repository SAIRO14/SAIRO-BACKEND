package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.place.TourApiClient;
import com.sairo.sairo_backend.spot.Spot;
import com.sairo.sairo_backend.spot.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CuratedCourseService {

    private static final int COURSES_PER_REGION = 3;

    // 우리 DB region_name → TourAPI areaCode
    private static final Map<String, String> AREA_CODE_MAP = Map.of(
            "제주도",     "39",
            "전라북도",   "35",
            "경상북도",   "37",
            "경상남도",   "38",
            "강원도",     "32",
            "충청남도",   "34",
            "서울특별시", "11"
    );

    private final TourApiClient tourApiClient;
    private final SpotRepository spotRepository;

    public List<CuratedCourseResponse> getCuratedCourses(String regionName) {
        String areaCode = AREA_CODE_MAP.get(regionName);
        if (areaCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 지역입니다: " + regionName);
        }

        List<TourApiClient.CourseItem> courseItems = tourApiClient.fetchCourseList(areaCode, COURSES_PER_REGION);
        if (courseItems.isEmpty()) return Collections.emptyList();

        return courseItems.stream()
                .map(item -> buildCuratedCourse(item, regionName))
                .filter(c -> !c.day1().isEmpty() || !c.day2().isEmpty())
                .collect(Collectors.toList());
    }

    private CuratedCourseResponse buildCuratedCourse(TourApiClient.CourseItem item, String regionName) {
        List<TourApiClient.CourseSpot> apiSpots = tourApiClient.fetchCourseSpots(item.contentId());

        // subcontentid 기준으로 우리 DB 일괄 조회
        List<String> subIds = apiSpots.stream()
                .map(TourApiClient.CourseSpot::subContentId)
                .collect(Collectors.toList());
        Map<String, Spot> dbSpots = spotRepository.findAllById(subIds).stream()
                .collect(Collectors.toMap(Spot::getSpotId, s -> s));

        // SpotSummary 조립 — DB 우선, 없으면 TourAPI detailCommon2로 좌표 보완
        List<SpotSummary> all = new ArrayList<>();
        for (TourApiClient.CourseSpot cs : apiSpots) {
            Spot db = dbSpots.get(cs.subContentId());
            if (db != null) {
                all.add(new SpotSummary(db.getSpotId(), db.getName(), db.getLat(), db.getLng(),
                        cs.imageUrl() != null ? cs.imageUrl() : db.getImageUrl()));
            } else {
                TourApiClient.SpotCoord coord = tourApiClient.fetchSpotCoord(cs.subContentId())
                        .orElse(null);
                all.add(new SpotSummary(
                        cs.subContentId(), cs.name(),
                        coord != null ? coord.lat() : null,
                        coord != null ? coord.lng() : null,
                        cs.imageUrl()
                ));
            }
        }

        int mid = all.size() / 2;
        return new CuratedCourseResponse(
                item.contentId(),
                item.title(),
                item.imageUrl(),
                regionName,
                all.subList(0, mid),
                all.subList(mid, all.size())
        );
    }
}
