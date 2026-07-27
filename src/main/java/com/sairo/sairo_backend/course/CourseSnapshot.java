package com.sairo.sairo_backend.course;

import java.util.List;

/**
 * `courses.course_data`와 `shared_courses.course_data`에 저장되는 JSON 형태다.
 *
 * <p>지역을 함께 담는다. 공유 코스는 "지역과 코스의 스냅샷"이므로
 * 지역이 빠지면 공유 상세에서 지역명을 표시할 수 없다.
 */
public record CourseSnapshot(
        String regionName,
        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
