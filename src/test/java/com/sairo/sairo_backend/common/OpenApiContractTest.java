package com.sairo.sairo_backend.common;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 생성된 OpenAPI 문서가 오류 계약과 일치하는지 검증한다.
 *
 * <p>"OpenAPI가 API 명세의 정본"이라는 결정(ADR 0002)은 생성 결과가 실제 응답과
 * 같을 때만 성립한다. 이 검증이 없던 동안 오류 응답 7개가 전부 성공 DTO 스키마로
 * 생성되고 있었다.
 */
class OpenApiContractTest extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode fetchApiDocs() throws Exception {
        byte[] body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        return MAPPER.readTree(new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void errorResponseSchema_isRegistered() throws Exception {
        JsonNode schemas = fetchApiDocs().path("components").path("schemas");

        assertThat(schemas.has("ErrorResponse"))
                .as("ErrorResponse가 components.schemas에 등록되어야 한다")
                .isTrue();

        JsonNode properties = schemas.path("ErrorResponse").path("properties");
        assertThat(properties.propertyNames())
                .contains("code", "message", "retryable", "traceId");
    }

    @Test
    void everyErrorResponse_usesErrorResponseSchema() throws Exception {
        JsonNode paths = fetchApiDocs().path("paths");
        List<String> violations = new ArrayList<>();

        paths.propertyStream().forEach(pathEntry ->
                pathEntry.getValue().propertyStream().forEach(methodEntry ->
                        methodEntry.getValue().path("responses").propertyStream().forEach(responseEntry -> {
                            String statusCode = responseEntry.getKey();
                            if (!statusCode.startsWith("4") && !statusCode.startsWith("5")) return;

                            String ref = responseEntry.getValue()
                                    .path("content").path("application/json").path("schema").path("$ref").asString("");

                            if (!ref.endsWith("/ErrorResponse")) {
                                violations.add("%s %s %s -> %s".formatted(
                                        methodEntry.getKey().toUpperCase(), pathEntry.getKey(), statusCode,
                                        ref.isEmpty() ? "(스키마 없음)" : ref));
                            }
                        })));

        assertThat(violations)
                .as("모든 4xx·5xx 응답은 ErrorResponse 스키마여야 한다")
                .isEmpty();
    }

    /**
     * {@code X-Device-Id}를 받는 엔드포인트는 400 설명에 디바이스 오류 코드를 반드시 남긴다.
     *
     * <p>{@code SwaggerConfig}가 헤더 400을 붙이지만, 컨트롤러가 다른 이유로 400을 이미
     * 선언한 엔드포인트에서는 그 설명을 그대로 두고 돌아가 {@code DEVICE_ID_*}가 명세에서
     * 사라졌다. `POST /saved-trips`가 둘을 동시에 가진 첫 엔드포인트라 거기서 처음 드러났다.
     * 명세가 정본(ADR 0002)이고 클라이언트가 코드로 분기하므로 둘 다 남아야 한다.
     */
    @Test
    void deviceIdEndpoints_documentDeviceIdErrorCodes() throws Exception {
        JsonNode paths = fetchApiDocs().path("paths");
        List<String> violations = new ArrayList<>();

        paths.propertyStream().forEach(pathEntry ->
                pathEntry.getValue().propertyStream().forEach(methodEntry -> {
                    JsonNode operation = methodEntry.getValue();
                    boolean hasDeviceIdHeader = operation.path("parameters").valueStream()
                            .anyMatch(p -> "X-Device-Id".equals(p.path("name").asString("")));
                    if (!hasDeviceIdHeader) return;

                    String description = operation.path("responses").path("400").path("description").asString("");
                    if (!description.contains("DEVICE_ID_")) {
                        violations.add("%s %s -> \"%s\"".formatted(
                                methodEntry.getKey().toUpperCase(), pathEntry.getKey(), description));
                    }
                }));

        assertThat(violations)
                .as("X-Device-Id를 받는 엔드포인트의 400 설명에 DEVICE_ID_* 코드가 있어야 한다")
                .isEmpty();
    }

    // 오류 응답이 하나도 문서화되지 않으면 위 검증이 공허하게 통과한다.
    @Test
    void errorResponses_areDocumented() throws Exception {
        JsonNode paths = fetchApiDocs().path("paths");
        long errorResponseCount = paths.propertyStream()
                .flatMap(p -> p.getValue().propertyStream())
                .flatMap(m -> m.getValue().path("responses").propertyStream())
                .filter(r -> r.getKey().startsWith("4") || r.getKey().startsWith("5"))
                .count();

        assertThat(errorResponseCount)
                .as("컨트롤러에 오류 응답이 문서화되어 있어야 한다")
                .isGreaterThanOrEqualTo(7);
    }
}
