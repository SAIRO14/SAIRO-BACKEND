package com.sairo.sairo_backend.saved;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record SavedTripResponse(
        // 예시는 실제 검증을 통과하는 UUID v4여야 한다. 버전 자리는 4, variant 자리는 8·9·a·b다.
        @Schema(description = "저장 항목 ID", example = "1f0a2b3c-4d5e-4f70-8192-a3b4c5d6e7f8")
        String savedTripId,
        @Schema(description = "저장된 코스 ID. 같은 코스를 다시 저장하면 처음 저장할 때의 값이 그대로 나온다.")
        String courseId,
        @Schema(description = "저장된 지역명", example = "제주도")
        String regionName,
        @Schema(description = "저장 시각")
        LocalDateTime createdAt
) {
    // regionKey → regionName으로 이름이 바뀐다. 같은 값이다.
    // DB에서는 중복 판정 키로 쓰이고(region_key), 응답에서는 화면에 그대로 표시된다.
    static SavedTripResponse from(SavedTrip savedTrip) {
        return new SavedTripResponse(
                savedTrip.savedTripId(),
                savedTrip.courseId(),
                savedTrip.regionKey(),
                savedTrip.createdAt()
        );
    }
}
