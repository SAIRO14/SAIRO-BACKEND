package com.sairo.sairo_backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 도입 시점에 이미 테이블을 가지고 있던 DB가 깨지지 않는지 검증한다.
 *
 * <p>팀원들의 로컬 DB와 운영 DB에는 V1의 테이블이 이미 있다. 이 상태에서 애플리케이션이
 * 뜰 때 Flyway가 V1을 다시 실행하지 않고 기준선만 기록해야 한다.
 * {@code baseline-on-migrate}와 {@code baseline-version} 설정이 바뀌면 이 테스트가 깨진다.
 */
class FlywayBaselineTest {

    @Test
    void existingDatabase_isBaselined_withoutRerunningV1() throws Exception {
        String adminUrl = PostgresTestContainer.INSTANCE.getJdbcUrl();
        String user = PostgresTestContainer.INSTANCE.getUsername();
        String password = PostgresTestContainer.INSTANCE.getPassword();
        String dbName = "flyway_baseline_test";

        // Flyway를 모른 채 운영되던 기존 DB를 재현한다.
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + dbName);
            st.execute("CREATE DATABASE " + dbName);
        }

        String targetUrl = adminUrl.replaceFirst("/[^/?]+(\\?|$)", "/" + dbName + "$1");
        String v1 = new ClassPathResource("db/migration/V1__init_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        try (Connection conn = DriverManager.getConnection(targetUrl, user, password);
             Statement st = conn.createStatement()) {
            st.execute(v1);
        }

        // 애플리케이션과 동일한 설정으로 마이그레이션한다.
        var result = Flyway.configure()
                .dataSource(targetUrl, user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();

        // V1은 이미 반영된 상태이므로 다시 실행되지 않아야 한다.
        assertThat(result.migrationsExecuted).isZero();

        try (Connection conn = DriverManager.getConnection(targetUrl, user, password);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT version, type FROM flyway_schema_history ORDER BY installed_rank")) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("1");
            assertThat(rs.getString("type")).isEqualTo("BASELINE");
            assertThat(rs.next()).as("기준선 외에 적용된 마이그레이션이 없어야 한다").isFalse();
        }
    }
}
