package com.sairo.sairo_backend.saved;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SavedTripRequest(
        // 서버가 저장해둔 코스만 저장할 수 있다. 코스 내용을 본문으로 받지 않는 이유는
        // 공유 생성과 같다 — 서버가 만들지 않은 코스가 저장 목록에 들어오면 안 된다.
        //
        // 형식을 검증하는 이유는 이 값이 **본문**에 있기 때문이다. 경로의 리소스 ID는
        // 형식을 보지 않고 404로 답하지만(api-contract.md §2), 본문 값은 Bean Validation으로
        // 400을 낸다. courseId는 UUID.randomUUID()로 발급된다.
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "courseId는 UUID 정규형이어야 합니다."
        )
        @Schema(description = "저장할 코스 ID", example = "8f14e45f-ea8d-4f4a-9c1b-2c3d4e5f6a7b")
        String courseId
) {}
