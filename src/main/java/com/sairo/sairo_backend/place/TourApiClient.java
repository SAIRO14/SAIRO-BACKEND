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
    private final Duration retryElapsedLimit;

    public TourApiClient(
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.service-key}") String serviceKey,
            @Value("${tour-api.connect-timeout}") Duration connectTimeout,
            @Value("${tour-api.read-timeout}") Duration readTimeout,
            @Value("${tour-api.retry-elapsed-limit}") Duration retryElapsedLimit
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
        this.retryElapsedLimit = retryElapsedLimit;
    }

    public record TourDetail(String operatingHours, String closedDays, String parking, String contact) {}

    /**
     * 실패해도 예외를 올리지 않고 빈 값을 돌려준다. 장소 상세는 TourAPI 없이도 성립한다
     * (api-contract.md §6 부분 실패 처리).
     */
    public Optional<TourDetail> fetchDetail(String contentId) {
        long start = System.nanoTime();

        try {
            return attemptFetch(contentId);
        } catch (Exception e) {
            if (!shouldRetry(e, Duration.ofNanos(System.nanoTime() - start))) {
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
     * 재시도 여부는 <b>예외 종류와 남은 시간</b> 둘 다로 판단한다 (ADR 0017).
     *
     * <p>종류는 "다시 보내볼 가치가 있는가"만 가른다 — 5xx와 IO 오류는 순간적인 문제일 수 있지만
     * 4xx는 같은 요청을 보내도 결과가 같다.
     *
     * <p>시간이 실제 제약이다. 첫 시도가 {@code retryElapsedLimit}을 넘겨 실패했다면 상대가
     * <b>느리다</b>는 뜻이라 재시도하지 않는다. 리드 타임아웃(5초)으로 끝난 시도와 5초를 끌다
     * 500을 뱉은 시도는 <b>같은 비용을 쓴 것</b>이므로 같게 다룬다. 예외 종류로 가르면
     * 이 둘이 갈려 근거와 코드가 어긋난다.
     *
     * <p>이 규칙이 한 번의 {@code fetchDetail}에 상한을 준다 —
     * <b>{@code retryElapsedLimit} + 리드 타임아웃</b>(기본값으로 3초 + 5초 = 8초)이다.
     * 리드 타임아웃이 커넥트를 포함한 시도 전체의 데드라인이라 한 시도가 그 값을 넘지 못한다.
     */
    boolean shouldRetry(Exception e, Duration elapsed) {
        if (elapsed.compareTo(retryElapsedLimit) > 0) return false;

        return e instanceof HttpServerErrorException || e instanceof ResourceAccessException;
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
