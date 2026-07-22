package com.sairo.sairo_backend.common;

import com.sairo.sairo_backend.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
        var result = mockMvc.perform(get("/places/no-such-spot"))
                .andExpect(status().isNotFound())
                .andReturn();

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        mockMvc.perform(get("/places/no-such-spot"))
                .andExpect(jsonPath("$.traceId").isString());

        org.assertj.core.api.Assertions.assertThat(headerTraceId).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains(headerTraceId);
    }
}
