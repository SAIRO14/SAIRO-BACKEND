package com.sairo.sairo_backend;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

// Singleton 패턴 — JVM 전체에서 컨테이너 하나만 기동
// Spring 컨텍스트 캐싱과 Testcontainers 클래스 단위 생명주기 충돌 방지
public final class PostgresTestContainer {

    public static final PostgreSQLContainer<?> INSTANCE;

    static {
        INSTANCE = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("sairo")
                .withUsername("postgres")
                .withPassword("sairo1234")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("db/schema.sql"),
                        "/docker-entrypoint-initdb.d/01-schema.sql"
                );
        INSTANCE.start();
    }

    private PostgresTestContainer() {}
}
