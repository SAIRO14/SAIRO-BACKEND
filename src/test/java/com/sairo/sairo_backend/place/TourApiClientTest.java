package com.sairo.sairo_backend.place;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
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
            sleep(READ_TIMEOUT.toMillis() * 4);
            respond(exchange, 200, "{}");
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

    private TourApiClient startWith(Consumer<com.sun.net.httpserver.HttpExchange> handler) {
        server.createContext("/", handler::accept);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new TourApiClient(baseUrl, "test-key", CONNECT_TIMEOUT, READ_TIMEOUT);
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
