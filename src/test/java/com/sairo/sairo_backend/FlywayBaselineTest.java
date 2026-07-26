package com.sairo.sairo_backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
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
 *
 * <p>검증에 쓰는 설정은 테스트에 다시 적지 않고 **애플리케이션의 {@link FlywayProperties}를
 * 그대로 읽어온다.** 설정을 테스트에 하드코딩하면 {@code application.yaml}에서
 * {@code baseline-on-migrate}를 꺼도 테스트가 통과해 회귀를 잡지 못한다.
 */
class FlywayBaselineTest extends IntegrationTestBase {

    @Autowired
    FlywayProperties flywayProperties;

    /** 기존 DB 보호는 이 설정에 달려 있다. 바꾸려면 운영 DB 영향을 먼저 확인해야 한다. */
    @Test
    void applicationConfig_protectsExistingDatabases() {
        assertThat(flywayProperties.isBaselineOnMigrate())
                .as("기존 DB에서 V1이 재실행되지 않으려면 baseline-on-migrate가 켜져 있어야 한다")
                .isTrue();
        assertThat(flywayProperties.getBaselineVersion())
                .as("기준선이 1이어야 V1을 이미 적용된 것으로 간주한다")
                .isEqualTo("1");
        assertThat(flywayProperties.getLocations())
                .contains("classpath:db/migration");
    }

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

        // 애플리케이션 설정을 그대로 사용한다.
        var result = Flyway.configure()
                .dataSource(targetUrl, user, password)
                .locations(flywayProperties.getLocations().toArray(new String[0]))
                .baselineOnMigrate(flywayProperties.isBaselineOnMigrate())
                .baselineVersion(flywayProperties.getBaselineVersion())
                .load()
                .migrate();

        // V1은 이미 반영된 상태이므로 다시 실행되지 않아야 한다.
        // 그 뒤의 마이그레이션(V2 이후)은 정상적으로 적용된다.
        assertThat(result.migrations)
                .as("V1이 다시 실행되면 이미 있는 테이블 위에 덮어쓰게 된다")
                .noneMatch(m -> "1".equals(m.version));

        try (Connection conn = DriverManager.getConnection(targetUrl, user, password);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT version, type FROM flyway_schema_history ORDER BY installed_rank")) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("1");
            assertThat(rs.getString("type"))
                    .as("V1은 실행이 아니라 기준선으로 기록되어야 한다")
                    .isEqualTo("BASELINE");

            while (rs.next()) {
                assertThat(rs.getString("version"))
                        .as("기준선 이후의 마이그레이션만 실행되어야 한다")
                        .isNotEqualTo("1");
                assertThat(rs.getString("type")).isEqualTo("SQL");
            }
        }
    }
}
