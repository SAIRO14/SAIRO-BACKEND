package com.sairo.sairo_backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SAIRO API")
                        .description("사진으로 발견하는 나만의 여행지 — 취향 분석 → 여행지 추천 → 1박 2일 코스 생성")
                        .version("v1.0"));
    }
}
