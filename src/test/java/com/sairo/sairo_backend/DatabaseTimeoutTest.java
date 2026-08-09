package com.sairo.sairo_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 쿼리 실행 상한이 실제 커넥션에 걸려 있는지 확인한다 (ADR 0017).
 *
 * <p>설정이 조용히 빠져도 기능 테스트는 전부 통과하므로 별도로 잡는다.
 * JPA 힌트가 아니라 서버 측 {@code statement_timeout}을 쓰는 이유는
 * {@code JdbcTemplate} 경로(pgvector 조회)까지 덮기 위해서다 — 그래서 확인도 JdbcTemplate으로 한다.
 */
class DatabaseTimeoutTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("커넥션에 statement_timeout 5초가 걸려 있다")
    void statementTimeoutIsApplied() {
        String timeout = jdbcTemplate.queryForObject("SHOW statement_timeout", String.class);

        assertThat(timeout).isEqualTo("5s");
    }
}
