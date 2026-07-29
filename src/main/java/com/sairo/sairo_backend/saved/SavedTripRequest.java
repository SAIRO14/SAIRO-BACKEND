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
        //
        // 대문자를 받지 않는다. courses.course_id는 TEXT라 조회가 대소문자를 구분하므로,
        // 대문자를 통과시키면 실재하는 코스에 COURSE_NOT_FOUND 404가 나간다.
        // 서버가 발급한 적 없는 형식이므로 400으로 거절하는 편이 정직하다.
        // (@DeviceId는 클라이언트가 생성하는 값이라 소문자로 정규화한다. 성격이 다르다.)
        @NotBlank
        // 버전 자리(4)와 IETF variant(8·9·a·b)까지 본다. courseId는 UUID.randomUUID()로
        // 발급되므로 항상 v4다. 대문자를 거절하는 것과 같은 이유로 비-v4도 걸러야 논리가 맞는다.
        @Pattern(
                regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
                message = "courseId는 소문자 UUID v4 정규형이어야 합니다."
        )
        @Schema(description = "저장할 코스 ID", example = "8f14e45f-ea8d-4f4a-9c1b-2c3d4e5f6a7b")
        String courseId
) {}
