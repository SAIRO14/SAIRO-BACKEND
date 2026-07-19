package com.sairo.sairo_backend.place;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
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

    public record CourseItem(String contentId, String title, String imageUrl) {}

    public List<CourseItem> fetchCourseList(String areaCode, int limit) {
        try {
            JsonNode root = restClient.get()
                    .uri("/areaBasedList2?serviceKey={key}&numOfRows={limit}&pageNo=1" +
                         "&MobileOS=ETC&MobileApp=SAIRO&_type=json&arrange=Q&contentTypeId=25&areaCode={area}",
                            serviceKey, limit, areaCode)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) return List.of();
            JsonNode items = root.path("response").path("body").path("items").path("item");
            List<CourseItem> result = new ArrayList<>();
            Iterable<JsonNode> nodes = items.isArray() ? items : List.of(items);
            for (JsonNode node : nodes) {
                result.add(new CourseItem(
                        node.path("contentid").asText(),
                        node.path("title").asText(),
                        nullIfBlank(node.path("firstimage").asText(null))
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("TourAPI 코스 목록 조회 실패 areaCode={}: {}", areaCode, e.getMessage());
            return List.of();
        }
    }

    public record CourseSpot(String subContentId, String name, String imageUrl, int order) {}

    public List<CourseSpot> fetchCourseSpots(String contentId) {
        try {
            JsonNode root = restClient.get()
                    .uri("/detailInfo2?serviceKey={key}&contentId={id}&contentTypeId=25" +
                         "&MobileOS=ETC&MobileApp=SAIRO&_type=json",
                            serviceKey, contentId)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) return List.of();
            JsonNode items = root.path("response").path("body").path("items").path("item");
            List<CourseSpot> result = new ArrayList<>();
            Iterable<JsonNode> nodes = items.isArray() ? items : List.of(items);
            for (JsonNode node : nodes) {
                result.add(new CourseSpot(
                        node.path("subcontentid").asText(),
                        node.path("subname").asText(),
                        nullIfBlank(node.path("subdetailimg").asText(null)),
                        node.path("subnum").asInt()
                ));
            }
            result.sort(java.util.Comparator.comparingInt(CourseSpot::order));
            return result;
        } catch (Exception e) {
            log.warn("TourAPI 코스 장소 조회 실패 contentId={}: {}", contentId, e.getMessage());
            return List.of();
        }
    }

    public record SpotCoord(Double lat, Double lng) {}

    public Optional<SpotCoord> fetchSpotCoord(String contentId) {
        try {
            JsonNode root = restClient.get()
                    .uri("/detailCommon2?serviceKey={key}&contentId={id}" +
                         "&MobileOS=ETC&MobileApp=SAIRO&_type=json&defaultYN=Y&firstImageYN=N&areainfoYN=N&addrinfoYN=N&mapinfoYN=Y&overviewYN=N",
                            serviceKey, contentId)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) return Optional.empty();
            JsonNode items = root.path("response").path("body").path("items").path("item");
            JsonNode item = items.isArray() ? items.get(0) : items;
            if (item == null || item.isMissingNode()) return Optional.empty();

            String mapx = item.path("mapx").asText(null);
            String mapy = item.path("mapy").asText(null);
            if (mapx == null || mapy == null || mapx.isBlank() || mapy.isBlank()) return Optional.empty();

            return Optional.of(new SpotCoord(Double.parseDouble(mapy), Double.parseDouble(mapx)));
        } catch (Exception e) {
            log.warn("TourAPI 좌표 조회 실패 contentId={}: {}", contentId, e.getMessage());
            return Optional.empty();
        }
    }

    private String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
