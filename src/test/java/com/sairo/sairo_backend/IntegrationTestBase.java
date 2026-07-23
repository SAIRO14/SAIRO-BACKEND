package com.sairo.sairo_backend;

import com.sairo.sairo_backend.common.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainer.INSTANCE::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    WebApplicationContext wac;

    @Autowired
    TraceIdFilter traceIdFilter;

    protected MockMvc mockMvc;

    // 필터를 함께 등록해야 traceId 발급과 X-Trace-Id 헤더가 실제 요청과 같게 동작한다.
    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(traceIdFilter)
                .build();
    }
}
