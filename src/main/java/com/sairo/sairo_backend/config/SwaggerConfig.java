package com.sairo.sairo_backend.config;

import com.sairo.sairo_backend.common.DeviceId;
import com.sairo.sairo_backend.common.DeviceIdArgumentResolver;
import com.sairo.sairo_backend.common.ErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;

/**
 * Swagger UI: {@code /swagger-ui.html}, OpenAPI 문서: {@code /v3/api-docs}
 *
 * <p>API 명세의 정본은 이 코드에서 생성되는 OpenAPI 문서다.
 * 별도 명세 문서를 두지 않으므로 컨트롤러의 어노테이션을 항상 최신으로 유지한다.
 * 작성 규칙은 AGENTS.md의 "API 명세" 절을 따른다.
 */
@Configuration
public class SwaggerConfig {

    // @DeviceId는 커스텀 리졸버가 채우는 값이라 springdoc이 그대로 두면 쿼리 파라미터로 문서화한다.
    // 여기서 무시시키고 아래 deviceIdHeaderCustomizer가 헤더 파라미터로 다시 넣는다.
    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(DeviceId.class);
    }

    private static final String DEVICE_ID_DESCRIPTION =
            "익명 사용자 식별자. 앱 최초 실행 시 클라이언트가 생성해 기기에 보관하는 UUID v4다. "
                    + "형식이 틀리면 400 DEVICE_ID_INVALID.";

    /** UUID v4 예시. 버전 자리(4)와 variant 자리(8·9·a·b)가 실제 검증을 통과하는 값이어야 한다. */
    private static final String DEVICE_ID_EXAMPLE = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

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

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SAIRO API")
                        .description(ERROR_CONTRACT_DESCRIPTION)
                        .version("v1.0"));
    }

    /**
     * 모든 4xx·5xx 응답의 본문 스키마를 {@link ErrorResponse}로 맞춘다.
     *
     * <p>컨트롤러마다 {@code content = @Content(schema = ...)}를 반복해 적으면
     * 하나만 빠뜨려도 그 엔드포인트의 명세가 틀어진다. 실제로 이 설정을 넣기 전에는
     * 오류 응답 7개가 모두 성공 DTO 스키마로 생성되고 있었다.
     *
     * <p>여기서 일괄 처리하면 앞으로 추가되는 엔드포인트도 자동으로 적용된다.
     * 개별 컨트롤러는 상태 코드와 {@code ErrorCode} 이름만 설명에 적으면 된다.
     */
    @Bean
    public OpenApiCustomizer errorResponseSchemaCustomizer() {
        return openApi -> {
            registerErrorSchema(openApi);

            Content errorContent = new Content().addMediaType(
                    org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                    new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));

            if (openApi.getPaths() == null) return;

            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        if (operation.getResponses() == null) return;
                        operation.getResponses().forEach((statusCode, response) -> {
                            if (statusCode.startsWith("4") || statusCode.startsWith("5")) {
                                response.setContent(errorContent);
                            }
                        });
                    }));
        };
    }

    /**
     * {@link DeviceId} 파라미터를 가진 엔드포인트에 {@code X-Device-Id} 헤더를 문서화한다.
     *
     * <p>컨트롤러마다 {@code @Parameter(in = HEADER, ...)}를 반복해 적으면 하나만 빠뜨려도
     * 그 엔드포인트의 명세가 틀어진다. 필수 여부도 구현(파라미터 타입)에서 그대로 끌어온다.
     */
    @Bean
    public OperationCustomizer deviceIdHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                if (!parameter.hasParameterAnnotation(DeviceId.class)) continue;

                boolean required = !DeviceIdArgumentResolver.isOptional(parameter);
                operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
                        .in("header")
                        .name(DeviceIdArgumentResolver.HEADER_NAME)
                        .description(DEVICE_ID_DESCRIPTION)
                        .required(required)
                        .example(DEVICE_ID_EXAMPLE)
                        .schema(new StringSchema().format("uuid")));
            }
            return operation;
        };
    }

    private void registerErrorSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        ModelConverters.getInstance()
                .readAll(new AnnotatedType(ErrorResponse.class))
                .forEach(openApi.getComponents()::addSchemas);
    }
}
