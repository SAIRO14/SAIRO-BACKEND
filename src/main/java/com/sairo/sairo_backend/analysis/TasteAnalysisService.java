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
                .map(region -> Map.entry(region, spotRepository.findByRegionContaining(region, SPOTS_PER_REGION)))
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> {
                    // 부분 일치 조회라 서로 다른 region_name 장소가 섞일 수 있다. 첫 장소 기준으로 정규화한다.
                    String canonical = e.getValue().get(0).getRegionName();
                    if (canonical == null) return Map.entry(e.getKey(), List.<Spot>of());
                    List<Spot> consistent = e.getValue().stream()
                            .filter(s -> canonical.equals(s.getRegionName()))
                            .collect(Collectors.toList());
                    return Map.entry(e.getKey(), consistent);
                })
                .filter(e -> e.getValue().size() >= MIN_SPOTS_FOR_REGION)
                .map(e -> buildRegionCard(e.getKey(), e.getValue(), reason))
                .collect(Collectors.toList());

        return new RecommendationResponse(entry.moodTags(), regions);
    }

    private RecommendationResponse.RegionCard buildRegionCard(String region, List<Spot> spots, String reason) {
        String imageUrl = spots.stream()
                .map(Spot::getImageUrl)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        List<RecommendationResponse.PreviewSpot> previewSpots = spots.stream()
                .limit(PREVIEW_SPOT_COUNT)
                .map(s -> new RecommendationResponse.PreviewSpot(s.getSpotId(), s.getName()))
                .collect(Collectors.toList());
        return new RecommendationResponse.RegionCard(region, region, imageUrl, reason, false, previewSpots);
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
