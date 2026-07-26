package com.sairo.sairo_backend.course;

import java.util.List;

public record SharedCourseViewResponse(
        String shareId,
        String regionName,
        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
