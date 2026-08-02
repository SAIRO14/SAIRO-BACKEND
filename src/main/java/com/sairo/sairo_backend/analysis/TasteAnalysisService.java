package com.sairo.sairo_backend.analysis;

import com.sairo.sairo_backend.common.BusinessException;
import com.sairo.sairo_backend.common.ErrorCode;
import com.sairo.sairo_backend.photo.Photo;
import com.sairo.sairo_backend.photo.PhotoEmbeddingRepository;
import com.sairo.sairo_backend.photo.PhotoRepository;
import com.sairo.sairo_backend.spot.Spot;
import com.sairo.sairo_backend.spot.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TasteAnalysisService {

    private static final int MIN_PHOTO_COUNT = 5;
    private static final int SIMILAR_PHOTO_LIMIT = 30;
    private static final int SPOTS_PER_REGION = 5;
    private static final int TOP_REGION_COUNT = 3;
    private static final int MIN_SPOTS_FOR_REGION = 2;
    private static final int PREVIEW_SPOT_COUNT = 2;
    private static final double CLUSTER_RADIUS_KM = 40.0;
    private static final int CLUSTER_POOL_SIZE = 20;

    private final PhotoRepository photoRepository;
    private final PhotoEmbeddingRepository embeddingRepository;
    private final SpotRepository spotRepository;
    private final AnalysisStore analysisStore;

    public TasteAnalysisResponse analyze(List<String> photoIds) {
        // 중복 ID는 사실상 더 적은 사진으로 분석하는 것과 같다. 중복 제거 후 재확인한다.
        List<String> uniqueIds = photoIds.stream().distinct().collect(Collectors.toList());
        if (uniqueIds.size() < MIN_PHOTO_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_PHOTO_SELECTION,
                    "중복을 제거하면 5장 미만입니다.");
        }

        Map<String, float[]> embeddings = embeddingRepository.findEmbeddingsByIds(uniqueIds);
        if (embeddings.size() < MIN_PHOTO_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_PHOTO_SELECTION,
                    "유효한 사진이 5장 미만입니다.");
        }

        float[] avgEmbedding = average(new ArrayList<>(embeddings.values()));

        List<Photo> photos = photoRepository.findAllById(uniqueIds);
        List<String> moodTags = parseMoodTags(photos);
        String analysisId = analysisStore.save(avgEmbedding, moodTags);

        String summary = buildSummary(moodTags);

        return new TasteAnalysisResponse(analysisId, moodTags, summary);
    }

    public RecommendationResponse recommend(String analysisId) {
        AnalysisStore.AnalysisEntry entry = analysisStore.find(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_NOT_FOUND));

        List<PhotoEmbeddingRepository.SimilarPhoto> similarPhotos =
                embeddingRepository.findSimilarPhotos(entry.embedding(), SIMILAR_PHOTO_LIMIT);

        List<String> topRegions = extractTopRegions(similarPhotos);
        String reason = MoodReasonMapper.from(entry.moodTags());

        List<RecommendationResponse.RegionCard> regions = topRegions.stream()
                .map(region -> Map.entry(region, selectClusteredResult(region)))
                .filter(e -> !e.getValue().spots().isEmpty())
                .filter(e -> e.getValue().spots().get(0).getRegionName() != null)
                .map(e -> {
                    // 부분 일치 조회라 서로 다른 region_name 장소가 섞일 수 있다. 첫 장소 기준으로 정규화한다.
                    String canonical = e.getValue().spots().get(0).getRegionName();
                    List<Spot> consistent = e.getValue().spots().stream()
                            .filter(s -> canonical.equals(s.getRegionName()))
                            .collect(Collectors.toList());
                    return Map.entry(e.getKey(), new ClusterResult(e.getValue().areaName(), consistent));
                })
                .filter(e -> e.getValue().spots().size() >= MIN_SPOTS_FOR_REGION)
                .map(e -> buildRegionCard(e.getKey(), e.getValue().spots(), e.getValue().areaName(), reason))
                .collect(Collectors.toList());

        return new RecommendationResponse(entry.moodTags(), regions);
    }

    private RecommendationResponse.RegionCard buildRegionCard(String region, List<Spot> spots, String areaName, String reason) {
        String imageUrl = spots.stream()
                .map(Spot::getImageUrl)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        List<RecommendationResponse.PreviewSpot> previewSpots = spots.stream()
                .limit(PREVIEW_SPOT_COUNT)
                .map(s -> new RecommendationResponse.PreviewSpot(s.getSpotId(), s.getName()))
                .collect(Collectors.toList());
        return new RecommendationResponse.RegionCard(region, region, areaName, imageUrl, reason, false, previewSpots);
    }

    private record ClusterResult(String areaName, List<Spot> spots) {}

    /**
     * 밀집 클러스터 중심에서 반경 40km 이내 스팟 풀을 구성하고, 그 중 SPOTS_PER_REGION개를 랜덤 샘플링한다.
     * center의 area_name을 함께 반환해 지역 카드의 regionArea로 쓴다.
     *
     * <p>단순 LIMIT 쿼리는 spot_id 순서에 따라 지역 내에서 수백 km 떨어진 스팟이 묶일 수 있다.
     * 클러스터 샘플링을 쓰면 반경 40km 안에서만 스팟이 선택된다.
     *
     * <p>스팟 수가 SPOTS_PER_REGION 이하이면 클러스터 없이 그대로 반환한다.
     * 이 경우 첫 번째 스팟(spot_id 정렬 기준, 결정적)의 area_name을 사용한다.
     *
     * <p>풀이 CLUSTER_POOL_SIZE를 넘으면 셔플 후 상위 CLUSTER_POOL_SIZE개를 취한다.
     * 셔플을 먼저 해야 반경 내 모든 스팟이 풀에 포함될 확률이 균등해진다.
     *
     * <p>ponytail: 밀집 중심 탐색이 O(n²). 지역당 스팟 수가 수백 이하면 문제없다.
     * 데이터가 대폭 늘면 DB 쪽 공간 인덱스(PostGIS ST_DWithin)로 교체한다.
     */
    private ClusterResult selectClusteredResult(String region) {
        List<Spot> withCoords = spotRepository.findAllByRegionContainingWithCoords(region);
        if (withCoords.size() <= SPOTS_PER_REGION) {
            return new ClusterResult(resolveAreaName(withCoords), withCoords);
        }

        Spot center = withCoords.stream()
                .max(Comparator.comparingLong(s ->
                        withCoords.stream().filter(o -> distanceKm(s, o) <= CLUSTER_RADIUS_KM).count()))
                .orElseThrow();

        // center의 region_name으로 풀을 미리 정규화한다. 셔플 전에 확정해야
        // get(0).getRegionName()이 호출마다 달라지는 비결정성을 막을 수 있다.
        String centerRegion = center.getRegionName();
        List<Spot> pool = withCoords.stream()
                .filter(s -> distanceKm(center, s) <= CLUSTER_RADIUS_KM)
                .filter(s -> centerRegion.equals(s.getRegionName()))
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(pool);
        if (pool.size() > CLUSTER_POOL_SIZE) {
            pool = new ArrayList<>(pool.subList(0, CLUSTER_POOL_SIZE));
        }

        List<Spot> selected = new ArrayList<>(pool.subList(0, Math.min(SPOTS_PER_REGION, pool.size())));
        return new ClusterResult(resolveAreaName(selected), selected);
    }

    // 반환된 스팟들의 area_name이 모두 같으면 그대로, 여러 시군구에 걸치면 "{광역시도 축약} 일대"를 반환한다.
    // 광역시도 자체가 다르면 표시할 수 없으므로 null을 반환한다.
    private String resolveAreaName(List<Spot> spots) {
        List<String> areaNames = spots.stream()
                .map(Spot::getAreaName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (areaNames.isEmpty()) return null;
        if (areaNames.size() == 1) return areaNames.get(0);
        // area_name 형식이 "{광역시도 축약} {시군구}"이므로 첫 단어가 광역시도 축약명이다.
        Set<String> provinces = areaNames.stream()
                .map(a -> a.split(" ")[0])
                .collect(Collectors.toSet());
        if (provinces.size() > 1) return null;
        return provinces.iterator().next() + " 일대";
    }

    private double distanceKm(Spot a, Spot b) {
        final double R = 6371.0;
        double dLat = Math.toRadians(b.getLat() - a.getLat());
        double dLng = Math.toRadians(b.getLng() - a.getLng());
        double sinDLat = Math.sin(dLat / 2);
        double sinDLng = Math.sin(dLng / 2);
        double h = sinDLat * sinDLat
                + Math.cos(Math.toRadians(a.getLat())) * Math.cos(Math.toRadians(b.getLat())) * sinDLng * sinDLng;
        return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    // location 형식: "경상북도 안동", "제주도" 등 — 첫 번째 공백 이전 단어가 광역 지자체명
    private List<String> extractTopRegions(List<PhotoEmbeddingRepository.SimilarPhoto> photos) {
        return photos.stream()
                .map(PhotoEmbeddingRepository.SimilarPhoto::location)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .map(loc -> loc.split("\\s+")[0])
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_REGION_COUNT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // keywords 형식: "밀양 표충사, 재약산, 불교, 종교, 사찰" — 쉼표 구분
    private List<String> parseMoodTags(List<Photo> photos) {
        return photos.stream()
                .map(Photo::getKeywords)
                .filter(Objects::nonNull)
                .flatMap(kw -> Arrays.stream(kw.split(",")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String buildSummary(List<String> moodTags) {
        if (moodTags.isEmpty()) return "다양한 매력을 가진 여행 취향입니다.";
        String tags = String.join(", ", moodTags.subList(0, Math.min(3, moodTags.size())));
        return tags + " 느낌의 여행을 좋아하시는군요!";
    }

    private float[] average(List<float[]> embeddings) {
        int dim = embeddings.get(0).length;
        float[] avg = new float[dim];
        for (float[] e : embeddings) {
            for (int i = 0; i < dim; i++) avg[i] += e[i];
        }
        float n = embeddings.size();
        for (int i = 0; i < dim; i++) avg[i] /= n;
        return avg;
    }
}
