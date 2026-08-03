package com.sairo.sairo_backend.course;

import java.util.List;

/**
 * `courses.course_data`와 `shared_courses.course_data`에 저장되는 JSON 형태다.
 *
 * <p>지역과 카드 필드(regionArea, imageUrl, reason)를 함께 담는다.
 * POST /courses 경유 코스와 이전 스냅샷에서 카드 필드는 null이다.
 */
public record CourseSnapshot(
        String regionName,
        String regionArea,
        String imageUrl,
        String reason,
        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
