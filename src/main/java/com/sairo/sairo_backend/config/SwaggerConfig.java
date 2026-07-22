package com.sairo.sairo_backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: {@code /swagger-ui.html}, OpenAPI 문서: {@code /v3/api-docs}
 *
 * <p>API 명세의 정본은 이 코드에서 생성되는 OpenAPI 문서다.
 * 별도 명세 문서를 두지 않으므로 컨트롤러의 어노테이션을 항상 최신으로 유지한다.
 * 작성 규칙은 AGENTS.md의 "API 명세" 절을 따른다.
 */
@Configuration
public class SwaggerConfig {

    private static final String ERROR_CONTRACT_DESCRIPTION = """
            사진으로 발견하는 나만의 여행지 — 취향 분석 → 여행지 추천 → 1박 2일 코스 생성

            ## 오류 응답
            모든 오류는 아래 형태로 반환된다.

            ```json
            {
              "code": "ANALYSIS_NOT_FOUND",
              "message": "분석 결과를 찾을 수 없습니다.",
              "retryable": false,
              "traceId": "3f8c1a20b4d1"
            }
            ```

            - `code` — 클라이언트 분기 기준. 문자열 값은 `ErrorCode` enum과 1:1로 대응한다.
            - `message` — 사람이 읽는 설명. 그대로 사용자에게 노출하지 않는다.
            - `retryable` — 같은 요청을 그대로 재시도했을 때 성공 가능성이 있는지.
            - `traceId` — 응답 헤더 `X-Trace-Id`와 같은 값이며 서버 로그와 대조할 수 있다.

            상태 코드는 잘못된 입력 400, 리소스 없음 404, 충돌 409, 서버 오류 500으로 구분한다.
            """;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SAIRO API")
                        .description(ERROR_CONTRACT_DESCRIPTION)
                        .version("v1.0"));
    }
}
