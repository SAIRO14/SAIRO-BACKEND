package com.sairo.sairo_backend.course;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SharedCourseViewResponse(
        String shareId,

        // 코스 영속화(ADR 0010) 이전 공유 링크에는 지역·카드 필드가 없어 null이다.
        @Schema(nullable = true) String regionName,
        @Schema(nullable = true) String regionArea,
        @Schema(nullable = true) String imageUrl,
        @Schema(nullable = true) String reason,

        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
