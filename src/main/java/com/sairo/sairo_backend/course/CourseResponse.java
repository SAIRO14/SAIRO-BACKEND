package com.sairo.sairo_backend.course;

import java.util.List;

public record CourseResponse(
        String courseId,
        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
