package com.sairo.sairo_backend.course;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CourseResponse(
        String courseId,
        String regionName,
        @Schema(nullable = true) String regionArea,
        @Schema(nullable = true) String imageUrl,
        @Schema(nullable = true) String reason,
        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
