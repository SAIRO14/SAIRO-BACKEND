package com.sairo.sairo_backend.common;

import com.sairo.sairo_backend.IntegrationTestBase;
import io.swagger.v3.oas.models.Operation;
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
