package com.sairo.sairo_backend.place;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

/**
 * TourAPI(KorService2) 호출.
 *
 * <p>타임아웃과 재시도 정책은 {@code docs/decisions/0017-request-timeouts.md}에서 정했다.
 * 값을 바꿀 때는 그 ADR을 함께 고친다.
 */
@Slf4j
@Component
public class TourApiClient {

    private final RestClient restClient;
    private final String serviceKey;

    public TourApiClient(
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.service-key}") String serviceKey,
            @Value("${tour-api.connect-timeout:3s}") Duration connectTimeout,
            @Value("${tour-api.read-timeout:5s}") Duration readTimeout
    ) {
        // RestClient.builder()는 맨 빌더라 타임아웃이 붙지 않는다. 기반인 JDK HttpClient의
        // 기본 리드 타임아웃이 무한이라 요청 팩토리를 직접 만들어 상한을 건다 (ADR 0017).
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.serviceKey = serviceKey;
    }

    public record TourDetail(String operatingHours, String closedDays, String parking, String contact) {}

    /**
     * 실패해도 예외를 올리지 않고 빈 값을 돌려준다. 장소 상세는 TourAPI 없이도 성립한다
     * (api-contract.md §6 부분 실패 처리).
     */
    public Optional<TourDetail> fetchDetail(String contentId) {
        try {
            return attemptFetch(contentId);
        } catch (Exception e) {
            if (!isRetryable(e)) {
                log.warn("TourAPI 호출 실패 contentId={}: {}", contentId, e.getMessage());
                return Optional.empty();
            }
            log.warn("TourAPI 호출 실패, 1회 재시도 contentId={}: {}", contentId, e.getMessage());
        }

        try {
            return attemptFetch(contentId);
        } catch (Exception e) {
            log.warn("TourAPI 재시도 실패 contentId={}: {}", contentId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 재시도는 커넥트 실패와 5xx에만 건다. **리드 타임아웃은 재시도하지 않는다** —
     * 상대가 느리다는 뜻이라 한 번 더 보내면 그 한 건이 코스당 외부 호출 예산을 혼자 쓴다
     * (ADR 0017).
     */
    private boolean isRetryable(Exception e) {
        if (e instanceof HttpServerErrorException) return true;

        if (e instanceof ResourceAccessException accessException) {
            Throwable cause = accessException.getCause();
            // HttpConnectTimeoutException이 HttpTimeoutException의 하위 타입이라 순서가 중요하다.
            if (cause instanceof HttpConnectTimeoutException) return true;
            if (cause instanceof HttpTimeoutException) return false;
            return true;
        }

        return false;
    }

    private Optional<TourDetail> attemptFetch(String contentId) {
        JsonNode root = restClient.get()
                .uri("/detailIntro2?serviceKey={key}&contentId={id}&contentTypeId=12" +
                     "&MobileOS=ETC&MobileApp=SAIRO&_type=json",
                        serviceKey, contentId)
                .retrieve()
                .body(JsonNode.class);

        if (root == null) return Optional.empty();

        JsonNode items = root.path("response").path("body").path("items").path("item");
        JsonNode item = items.isArray() ? items.get(0) : items;

        if (item == null || item.isMissingNode()) return Optional.empty();

        return Optional.of(new TourDetail(
                nullIfBlank(item.path("usetime").asText(null)),
                nullIfBlank(item.path("restdate").asText(null)),
                nullIfBlank(item.path("parking").asText(null)),
                nullIfBlank(item.path("infocenter").asText(null))
        ));
    }

    private String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
