package com.sairo.sairo_backend.course;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SharedCourseViewResponse(
        String shareId,

        // 코스 영속화(ADR 0010) 이전에 만들어진 공유 링크에는 지역이 없다.
        // 그 스냅샷은 day1·day2만 담고 있어 지역을 복원할 수 없다.
        @Schema(description = "지역명. 코스 영속화 이전에 만들어진 공유 링크에서는 null이다.",
                nullable = true)
        String regionName,

        List<SpotSummary> day1,
        List<SpotSummary> day2
) {}
