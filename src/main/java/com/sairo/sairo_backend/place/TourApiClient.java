package com.sairo.sairo_backend.place;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

@Slf4j
@Component
public class TourApiClient {

    private final RestClient restClient;
    private final String serviceKey;

    public TourApiClient(
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.service-key}") String serviceKey
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.serviceKey = serviceKey;
    }

    public record TourDetail(String operatingHours, String closedDays, String parking, String contact) {}

    public Optional<TourDetail> fetchDetail(String contentId) {
        try {
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
        } catch (Exception e) {
            log.warn("TourAPI 호출 실패 contentId={}: {}", contentId, e.getMessage());
            return Optional.empty();
        }
    }

    private String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
