package com.sairo.sairo_backend;

import org.testcontainers.containers.PostgreSQLContainer;

// Singleton 패턴 — JVM 전체에서 컨테이너 하나만 기동
// Spring 컨텍스트 캐싱과 Testcontainers 클래스 단위 생명주기 충돌 방지
//
// 스키마는 initdb로 넣지 않고 Flyway가 적용한다.
// 그래야 마이그레이션 파일 자체가 CI에서 검증된다.
public final class PostgresTestContainer {

    public static final PostgreSQLContainer<?> INSTANCE;

    static {
        INSTANCE = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("sairo")
                .withUsername("postgres")
                .withPassword("sairo1234");
        INSTANCE.start();
    }

    private PostgresTestContainer() {}
}
