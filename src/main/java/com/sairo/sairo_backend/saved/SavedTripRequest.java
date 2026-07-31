package com.sairo.sairo_backend.saved;

import com.sairo.sairo_backend.common.IdFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SavedTripRequest(
        // 서버가 저장해둔 코스만 저장할 수 있다. 코스 내용을 본문으로 받지 않는 이유는
        // 공유 생성과 같다 — 서버가 만들지 않은 코스가 저장 목록에 들어오면 안 된다.
        //
        // 대문자나 비-v4 UUID를 통과시키면 TEXT 컬럼 조회가 빗나가, 실재하는 코스를 두고
        // COURSE_NOT_FOUND로 답할 수 있으므로 검증한다. 값의 위치가 아니라 거짓 응답 가능성이
        // 기준이다. (api-contract.md §2, ADR 0013)
        @NotBlank
        @Pattern(
                regexp = IdFormat.UUID_V4,
                message = "courseId는 소문자 UUID v4 정규형이어야 합니다."
        )
        @Schema(description = "저장할 코스 ID", example = "8f14e45f-ea8d-4f4a-9c1b-2c3d4e5f6a7b")
        String courseId
) {}
