package com.sairo.sairo_backend.common;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 오류 응답 계약이 모든 API에서 동일한 형태로 유지되는지 검증한다.
 *
 * <p>개별 API 테스트는 상태 코드만 확인하므로, 본문 구조는 여기서 한 곳에서 지킨다.
 */
class ErrorContractTest extends IntegrationTestBase {

    @Test
    void notFound_hasStandardErrorShape() throws Exception {
        mockMvc.perform(get("/places/no-such-spot"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void validationFailure_usesInvalidRequestCode() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .header("X-Device-Id", "f47ac10b-58cc-4372-a567-0e02b2c3d479")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photoIds": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void missingRequiredParam_usesInvalidRequestCode() throws Exception {
        mockMvc.perform(get("/recommendations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void everyResponse_carriesTraceIdHeader() throws Exception {
        mockMvc.perform(get("/photos").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));
    }

    // 오류 본문의 traceId와 응답 헤더의 추적 ID는 같은 값이어야 로그 추적이 성립한다.
    @Test
    void errorBodyTraceId_matchesResponseHeader() throws Exception {
        var response = mockMvc.perform(get("/places/no-such-spot"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse();

        String headerTraceId = response.getHeader("X-Trace-Id");
        assertThat(headerTraceId).isNotBlank();
        assertThat(response.getContentAsString()).contains("\"traceId\":\"" + headerTraceId + "\"");
    }

    // ─── 프로토콜 수준 오류 ──────────────────────────────────────────────────
    // 아래는 Spring이 기본 처리하던 4xx다. 포괄 Exception 핸들러가 이를 가로채
    // 전부 500으로 만든 회귀가 있었으므로 상태 코드를 고정한다.

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .header("X-Device-Id", "f47ac10b-58cc-4372-a567-0e02b2c3d479")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\": ["))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void unsupportedContentType_returns415() throws Exception {
        mockMvc.perform(post("/taste-analysis")
                        .header("X-Device-Id", "f47ac10b-58cc-4372-a567-0e02b2c3d479")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unsupportedMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/photos"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownPath_returns404() throws Exception {
        mockMvc.perform(get("/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }

    // 검증 없이 두면 음수가 DB까지 내려가 500이 된다.
    @Test
    void negativeLimit_returns400() throws Exception {
        mockMvc.perform(get("/photos").param("limit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void excessiveLimit_returns400() throws Exception {
        mockMvc.perform(get("/photos").param("limit", "10000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
