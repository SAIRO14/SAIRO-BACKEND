package com.sairo.sairo_backend.saved;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record SavedTripResponse(
        @Schema(description = "저장 항목 ID", example = "1f0a2b3c-4d5e-4f70-8192-a3b4c5d6e7f8")
        String savedTripId,
        @Schema(description = "저장된 코스 ID. 같은 코스를 다시 저장하면 처음 저장할 때의 값이 그대로 나온다.")
        String courseId,
        @Schema(description = "저장된 지역명", example = "제주도")
        String regionName,
        @Schema(description = "지역 소재지. POST /courses 경유 코스나 이전 저장 항목에서는 null이다.", nullable = true)
        String regionArea,
        @Schema(description = "코스 대표 이미지 URL. null일 수 있다.", nullable = true)
        String imageUrl,
        @Schema(description = "추천 이유 문구. null일 수 있다.", nullable = true)
        String reason,
        @Schema(description = "저장 시각")
        LocalDateTime createdAt
) {
    // regionKey → regionName으로 이름이 바뀐다. 같은 값이다.
    static SavedTripResponse from(SavedTrip savedTrip) {
        return new SavedTripResponse(
                savedTrip.savedTripId(),
                savedTrip.courseId(),
                savedTrip.regionKey(),
                savedTrip.regionArea(),
                savedTrip.imageUrl(),
                savedTrip.reason(),
                savedTrip.createdAt()
        );
    }
}
