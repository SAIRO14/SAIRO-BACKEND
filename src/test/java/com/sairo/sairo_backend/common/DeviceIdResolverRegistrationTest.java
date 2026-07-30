package com.sairo.sairo_backend.common;

import com.sairo.sairo_backend.IntegrationTestBase;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DeviceIdArgumentResolver}가 실제 애플리케이션에 등록되는지 확인한다.
 *
 * <p>해석 규칙 자체는 {@link DeviceIdArgumentResolverTest}가 검증한다. 여기서는 배선만 본다.
 * 규칙이 아무리 맞아도 {@code WebMvcConfig} 등록이 빠지면 헤더가 조용히 무시된다.
 */
class DeviceIdResolverRegistrationTest extends IntegrationTestBase {

    @RestController
    static class StubController {
        String required(@DeviceId String deviceId) {
            return deviceId;
        }

        String optional(@DeviceId Optional<String> deviceId) {
            return deviceId.orElse("");
        }
    }

    @Autowired
    RequestMappingHandlerAdapter handlerAdapter;

    @Autowired
    OperationCustomizer deviceIdHeaderCustomizer;

    @Test
    void deviceIdResolver_isRegisteredAsArgumentResolver() {
        List<HandlerMethodArgumentResolver> resolvers = handlerAdapter.getArgumentResolvers();

        assertThat(resolvers)
                .as("@DeviceId를 해석할 리졸버가 등록되어야 한다")
                .hasAtLeastOneElementOfType(DeviceIdArgumentResolver.class);
    }

    @Test
    void deviceIdHeaderCustomizer_documentsRequiredHeader() throws Exception {
        Operation operation = customize(StubController.class.getDeclaredMethod("required", String.class));

        assertThat(operation.getParameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.getIn()).isEqualTo("header");
            assertThat(parameter.getName()).isEqualTo(DeviceIdArgumentResolver.HEADER_NAME);
            assertThat(parameter.getRequired()).isTrue();
        });
    }

    // 헤더만 문서화하고 오류를 컨트롤러에 맡기면 저장 API가 늘어날 때마다 빠뜨릴 자리가 생긴다.
    @Test
    void deviceIdHeaderCustomizer_documentsRequiredHeaderErrors() throws Exception {
        Operation operation = customize(StubController.class.getDeclaredMethod("required", String.class));

        assertThat(operation.getResponses().get("400").getDescription())
                .contains("DEVICE_ID_REQUIRED", "DEVICE_ID_INVALID");
    }

    // 선택 헤더는 누락이 오류가 아니므로 DEVICE_ID_REQUIRED가 나오면 안 된다.
    @Test
    void deviceIdHeaderCustomizer_documentsOptionalHeaderErrors() throws Exception {
        Operation operation = customize(StubController.class.getDeclaredMethod("optional", Optional.class));

        assertThat(operation.getResponses().get("400").getDescription())
                .contains("DEVICE_ID_INVALID")
                .doesNotContain("DEVICE_ID_REQUIRED");
    }

    /**
     * 컨트롤러가 적은 설명을 덮지 않되, 디바이스 오류 코드를 이어 붙인다.
     *
     * <p>덮어쓰면 도메인 오류 코드가 사라지고, 그대로 두고 돌아가면 {@code DEVICE_ID_*}가 사라진다.
     * 한 상태 코드에 원인이 둘 이상인 것은 정상이므로 둘 다 남아야 한다.
     */
    @Test
    void deviceIdHeaderCustomizer_appendsToExistingErrorDescription() throws Exception {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("400", new ApiResponse().description("INVALID_REQUEST — courseId 누락")));

        HandlerMethod handlerMethod = new HandlerMethod(
                new StubController(), StubController.class.getDeclaredMethod("required", String.class));
        deviceIdHeaderCustomizer.customize(operation, handlerMethod);

        assertThat(operation.getResponses().get("400").getDescription())
                .contains("INVALID_REQUEST — courseId 누락")
                .contains("DEVICE_ID_REQUIRED", "DEVICE_ID_INVALID");
    }

    // 컨트롤러가 디바이스 오류를 직접 적었다면 그 설명이 더 구체적이다. 두 번 적지 않는다.
    @Test
    void deviceIdHeaderCustomizer_doesNotDuplicateDeviceIdCodes() throws Exception {
        String written = "DEVICE_ID_REQUIRED — 이 API는 헤더가 반드시 필요하다";
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("400", new ApiResponse().description(written)));

        HandlerMethod handlerMethod = new HandlerMethod(
                new StubController(), StubController.class.getDeclaredMethod("required", String.class));
        deviceIdHeaderCustomizer.customize(operation, handlerMethod);

        assertThat(operation.getResponses().get("400").getDescription()).isEqualTo(written);
    }

    // Optional 파라미터는 선택 헤더로 문서화돼야 한다. (추천 조회·장소 상세)
    @Test
    void deviceIdHeaderCustomizer_documentsOptionalHeader() throws Exception {
        Operation operation = customize(StubController.class.getDeclaredMethod("optional", Optional.class));

        assertThat(operation.getParameters()).singleElement().satisfies(parameter ->
                assertThat(parameter.getRequired()).isFalse());
    }

    private Operation customize(java.lang.reflect.Method method) {
        HandlerMethod handlerMethod = new HandlerMethod(new StubController(), method);
        return deviceIdHeaderCustomizer.customize(new Operation(), handlerMethod);
    }
}
