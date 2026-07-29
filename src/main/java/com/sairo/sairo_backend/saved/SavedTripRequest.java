package com.sairo.sairo_backend.saved;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SavedTripRequest(
        // 서버가 저장해둔 코스만 저장할 수 있다. 코스 내용을 본문으로 받지 않는 이유는
        // 공유 생성과 같다 — 서버가 만들지 않은 코스가 저장 목록에 들어오면 안 된다.
        @NotBlank
        @Schema(description = "저장할 코스 ID", example = "8f14e45f-ea8d-4f4a-9c1b-2c3d4e5f6a7b")
        String courseId
) {}
