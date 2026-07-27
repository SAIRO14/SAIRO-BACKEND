package com.sairo.sairo_backend.config;

import com.sairo.sairo_backend.common.DeviceIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 컨트롤러 파라미터 확장을 등록한다.
 *
 * <p>{@code @EnableWebMvc}를 붙이지 않는다. 붙이면 Spring Boot의 기본 MVC 설정이 꺼진다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final DeviceIdArgumentResolver deviceIdArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(deviceIdArgumentResolver);
    }
}
