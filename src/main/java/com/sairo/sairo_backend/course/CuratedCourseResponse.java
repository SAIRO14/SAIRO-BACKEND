package com.sairo.sairo_backend.course;

import java.util.List;

public record CuratedCourseResponse(
        String courseId,
        String title,
        String imageUrl,
        String regionName,
        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
