package com.sairo.sairo_backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code X-Device-Id} 헤더 해석 규칙을 고정한다. (docs/api-contract.md §4)
 *
 * <p>아직 이 헤더를 쓰는 엔드포인트가 없어 스텁 컨트롤러로 검증한다.
 * 저장 API가 붙으면 그쪽 통합 테스트가 실제 경로를 함께 덮는다.
 * 리졸버가 애플리케이션에 실제로 등록되는지는 {@link DeviceIdResolverRegistrationTest}가 본다.
 */
class DeviceIdArgumentResolverTest {

    private static final String VALID_UUID_V4 = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String NO_DEVICE_ID = "absent";

    @RestController
    static class StubController {

        @GetMapping("/stub/required")
        String required(@DeviceId String deviceId) {
            return deviceId;
        }

        // 스텁 응답은 ASCII만 쓴다. standaloneSetup의 기본 응답 인코딩이 UTF-8이 아니다.
        @GetMapping("/stub/optional")
        String optional(@DeviceId Optional<String> deviceId) {
            return deviceId.orElse(NO_DEVICE_ID);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
            .setCustomArgumentResolvers(new DeviceIdArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void requiredDeviceId_withValidHeader_returnsValue() throws Exception {
        mockMvc.perform(get("/stub/required").header(DeviceIdArgumentResolver.HEADER_NAME, VALID_UUID_V4))
                .andExpect(status().isOk())
                .andExpect(content().string(VALID_UUID_V4));
    }

    // 대소문자만 다른 값이 서로 다른 사용자로 갈리면 저장 목록이 기기별로 쪼개진다.
    @Test
    void requiredDeviceId_withUpperCaseHeader_isNormalizedToLowerCase() throws Exception {
        mockMvc.perform(get("/stub/required")
                        .header(DeviceIdArgumentResolver.HEADER_NAME, VALID_UUID_V4.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(content().string(VALID_UUID_V4));
    }

    @Test
    void requiredDeviceId_withoutHeader_returns400() throws Exception {
        mockMvc.perform(get("/stub/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));
    }

    @Test
    void requiredDeviceId_withBlankHeader_returns400() throws Exception {
        mockMvc.perform(get("/stub/required").header(DeviceIdArgumentResolver.HEADER_NAME, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));
    }

    @Test
    void requiredDeviceId_withMalformedHeader_returns400() throws Exception {
        mockMvc.perform(get("/stub/required").header(DeviceIdArgumentResolver.HEADER_NAME, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"));
    }

    // UUID.fromString은 축약형("1-1-1-1-1")도 통과시킨다. 정규형만 받는지 확인한다.
    @Test
    void requiredDeviceId_withAbbreviatedUuid_returns400() throws Exception {
        mockMvc.perform(get("/stub/required").header(DeviceIdArgumentResolver.HEADER_NAME, "1-1-1-1-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"));
    }

    // 형식은 UUID지만 v4가 아니다. 계약이 v4를 요구한다.
    @Test
    void requiredDeviceId_withNonV4Uuid_returns400() throws Exception {
        mockMvc.perform(get("/stub/required")
                        .header(DeviceIdArgumentResolver.HEADER_NAME, "f47ac10b-58cc-1372-a567-0e02b2c3d479"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"));
    }

    @Test
    void requiredDeviceId_withWrongVariant_returns400() throws Exception {
        mockMvc.perform(get("/stub/required")
                        .header(DeviceIdArgumentResolver.HEADER_NAME, "f47ac10b-58cc-4372-c567-0e02b2c3d479"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"));
    }

    @Test
    void optionalDeviceId_withoutHeader_returnsEmpty() throws Exception {
        mockMvc.perform(get("/stub/optional"))
                .andExpect(status().isOk())
                .andExpect(content().string(NO_DEVICE_ID));
    }

    // 선택이라도 값이 틀리면 실패시킨다. 조용히 무시하면 클라이언트가 원인을 알 수 없다.
    @Test
    void optionalDeviceId_withMalformedHeader_returns400() throws Exception {
        mockMvc.perform(get("/stub/optional").header(DeviceIdArgumentResolver.HEADER_NAME, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"));
    }

    @Test
    void optionalDeviceId_withValidHeader_returnsValue() throws Exception {
        mockMvc.perform(get("/stub/optional").header(DeviceIdArgumentResolver.HEADER_NAME, VALID_UUID_V4))
                .andExpect(status().isOk())
                .andExpect(content().string(VALID_UUID_V4));
    }
}
