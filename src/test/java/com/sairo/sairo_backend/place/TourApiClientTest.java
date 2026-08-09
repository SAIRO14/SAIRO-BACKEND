package com.sairo.sairo_backend.place;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 타임아웃과 재시도 정책 검증 (ADR 0017).
 *
 * <p>실제 값(커넥트 3s / 리드 5s)으로 돌리면 테스트가 그만큼 걸리므로
 * 생성자에 짧은 값을 넣어 같은 경로를 태운다.
 */
class TourApiClientTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    private static final Duration RETRY_ELAPSED_LIMIT = Duration.ofMillis(100);

    private HttpServer server;
    private final AtomicInteger callCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        callCount.set(0);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("리드 타임아웃은 재시도하지 않는다 — 호출 1회로 끝내고 빈 값을 준다")
    void readTimeoutIsNotRetried() {
        TourApiClient client = startWith(exchange -> {
            callCount.incrementAndGet();
            sleep(READ_TIMEOUT.toMillis() * 2);
            respond(exchange, 200, "{}");
        });

        Optional<TourApiClient.TourDetail> result = client.fetchDetail("spot-1");

        assertThat(result).isEmpty();
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("느리게 실패한 5xx는 재시도하지 않는다 — 리드 타임아웃과 같은 비용을 썼다")
    void slowServerErrorIsNotRetried() {
        TourApiClient client = startWith(exchange -> {
            callCount.incrementAndGet();
            sleep(RETRY_ELAPSED_LIMIT.toMillis() * 2);
            respond(exchange, 500, "{}");
        });

        Optional<TourApiClient.TourDetail> result = client.fetchDetail("spot-1");

        assertThat(result).isEmpty();
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("5xx는 1회 재시도한다 — 두 번째가 성공하면 값을 준다")
    void serverErrorIsRetriedOnce() {
        TourApiClient client = startWith(exchange -> {
            if (callCount.incrementAndGet() == 1) {
                respond(exchange, 500, "{}");
                return;
            }
            respond(exchange, 200, detailJson());
        });

        Optional<TourApiClient.TourDetail> result = client.fetchDetail("spot-1");

        assertThat(callCount.get()).isEqualTo(2);
        assertThat(result).isPresent();
        assertThat(result.get().operatingHours()).isEqualTo("09:00~18:00");
    }

    @Test
    @DisplayName("재시도까지 5xx면 예외를 올리지 않고 빈 값을 준다 (api-contract §6)")
    void giveUpAfterOneRetry() {
        TourApiClient client = startWith(exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 500, "{}");
        });

        Optional<TourApiClient.TourDetail> result = client.fetchDetail("spot-1");

        assertThat(result).isEmpty();
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("정상 응답이면 재시도하지 않는다")
    void successIsNotRetried() {
        TourApiClient client = startWith(exchange -> {
            callCount.incrementAndGet();
            respond(exchange, 200, detailJson());
        });

        Optional<TourApiClient.TourDetail> result = client.fetchDetail("spot-1");

        assertThat(result).isPresent();
        assertThat(callCount.get()).isEqualTo(1);
    }

    /**
     * 실제 커넥트 타임아웃은 환경에 따라 재현이 들쭉날쭉하므로 판단 함수를 직접 검증한다.
     * 서버를 띄우는 위 테스트들이 덮지 못하는 갈래다.
     */
    @Nested
    @DisplayName("shouldRetry — 예외 종류와 남은 시간")
    class ShouldRetry {

        private final TourApiClient client = clientFor("http://127.0.0.1:1");

        private final Duration fast = RETRY_ELAPSED_LIMIT.dividedBy(2);
        private final Duration slow = RETRY_ELAPSED_LIMIT.multipliedBy(2);

        @Test
        @DisplayName("빠른 커넥트 타임아웃은 재시도한다")
        void fastConnectTimeout() {
            Exception e = new ResourceAccessException("연결 실패", new HttpConnectTimeoutException("connect timed out"));

            assertThat(client.shouldRetry(e, fast)).isTrue();
        }

        @Test
        @DisplayName("빠른 5xx는 재시도한다")
        void fastServerError() {
            Exception e = HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR, "", HttpHeaders.EMPTY, new byte[0], null);

            assertThat(client.shouldRetry(e, fast)).isTrue();
        }

        @Test
        @DisplayName("4xx는 빨라도 재시도하지 않는다 — 다시 보내도 결과가 같다")
        void clientErrorIsNeverRetried() {
            Exception e = HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST, "", HttpHeaders.EMPTY, new byte[0], null);

            assertThat(client.shouldRetry(e, fast)).isFalse();
        }

        @Test
        @DisplayName("느리게 실패했으면 종류를 가리지 않고 재시도하지 않는다")
        void slowFailureIsNotRetried() {
            Exception readTimeout = new ResourceAccessException("응답 없음", new HttpTimeoutException("request timed out"));
            Exception serverError = HttpServerErrorException.create(
                    HttpStatus.SERVICE_UNAVAILABLE, "", HttpHeaders.EMPTY, new byte[0], null);

            assertThat(client.shouldRetry(readTimeout, slow)).isFalse();
            assertThat(client.shouldRetry(serverError, slow)).isFalse();
        }
    }

    private TourApiClient startWith(Consumer<com.sun.net.httpserver.HttpExchange> handler) {
        // 기본 실행자는 stop(0)이 잠든 디스패처 스레드를 join하느라 테스트를 붙잡는다.
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", handler::accept);
        server.start();
        return clientFor("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static TourApiClient clientFor(String baseUrl) {
        return new TourApiClient(baseUrl, "test-key", CONNECT_TIMEOUT, READ_TIMEOUT, RETRY_ELAPSED_LIMIT);
    }

    private static String detailJson() {
        return """
               {"response":{"body":{"items":{"item":[
                 {"usetime":"09:00~18:00","restdate":"연중무휴","parking":"가능","infocenter":"064-000-0000"}
               ]}}}}
               """;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
