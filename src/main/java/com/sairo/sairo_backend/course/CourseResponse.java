package com.sairo.sairo_backend.course;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CourseResponse(
        String courseId,
        String regionName,
        @Schema(nullable = true) String regionArea,
        @Schema(nullable = true) String imageUrl,
        @Schema(nullable = true) String reason,
        @Schema(description = "이 기기가 같은 장소 구성의 코스를 저장했는지 여부. courseId가 아니라 장소 구성으로 판정한다. (ADR 0011)")
        boolean saved,
        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
