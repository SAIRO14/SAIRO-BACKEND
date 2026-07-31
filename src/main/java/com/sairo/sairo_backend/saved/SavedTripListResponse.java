package com.sairo.sairo_backend.saved;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SavedTripListResponse(
        @Schema(description = "저장 항목. 최근 저장순이다.")
        List<SavedTripResponse> items,

        @Schema(
                description = """
                        다음 페이지 커서. 다음 페이지가 없으면 `null`이다.
                        내용을 해석하지 말고 받은 값을 그대로 `cursor`에 실어 다음 요청을 보낸다.
                        """,
                nullable = true,
                example = "djF8MTc1MzkyMDAwMDAwMDAwMHwxZjBhMmIzYy00ZDVlLTRmNzAtODE5Mi1hM2I0YzVkNmU3Zjg"
        )
        String nextCursor
) {}
