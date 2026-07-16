package com.sairo.sairo_backend.analysis;

import com.sairo.sairo_backend.photo.Photo;
import com.sairo.sairo_backend.photo.PhotoEmbeddingRepository;
import com.sairo.sairo_backend.photo.PhotoRepository;
import com.sairo.sairo_backend.spot.Spot;
import com.sairo.sairo_backend.spot.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TasteAnalysisService {

    private static final int SIMILAR_PHOTO_LIMIT = 30;
    private static final int SPOTS_PER_REGION = 5;
    private static final int TOP_REGION_COUNT = 3;

    private final PhotoRepository photoRepository;
    private final PhotoEmbeddingRepository embeddingRepository;
    private final SpotRepository spotRepository;
    private final AnalysisStore analysisStore;

    public TasteAnalysisResponse analyze(List<String> photoIds) {
        Map<String, float[]> embeddings = embeddingRepository.findEmbeddingsByIds(photoIds);
        if (embeddings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 photo_id가 없습니다.");
        }

        float[] avgEmbedding = average(new ArrayList<>(embeddings.values()));
        String analysisId = analysisStore.save(avgEmbedding);

        List<Photo> photos = photoRepository.findAllById(photoIds);
        List<String> moodTags = parseMoodTags(photos);
        String summary = buildSummary(moodTags);

        return new TasteAnalysisResponse(analysisId, moodTags, summary);
    }

    public List<RecommendationResponse> recommend(String analysisId) {
        float[] embedding = analysisStore.find(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 analysis_id입니다."));

        List<PhotoEmbeddingRepository.SimilarPhoto> similarPhotos =
                embeddingRepository.findSimilarPhotos(embedding, SIMILAR_PHOTO_LIMIT);

        List<String> topRegions = extractTopRegions(similarPhotos);
        if (topRegions.isEmpty()) return Collections.emptyList();

        return topRegions.stream()
                .flatMap(region -> spotRepository.findByRegionContaining(region, SPOTS_PER_REGION).stream()
                        .map(spot -> toResponse(spot, region)))
                .collect(Collectors.toList());
    }

    // ── 데이터 형식 의존 메서드 ─────────────────────────────────────────────
    // TODO: deduped_results.json의 실제 location 형식 확인 후 수정
    // 현재: location 필드 자체를 region 키로 사용
    private List<String> extractTopRegions(List<PhotoEmbeddingRepository.SimilarPhoto> photos) {
        return photos.stream()
                .map(PhotoEmbeddingRepository.SimilarPhoto::location)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(loc -> loc, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_REGION_COUNT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // TODO: deduped_results.json의 실제 keywords 구분자 확인 후 수정
    // 현재: 쉼표, 공백, # 복합 구분자로 파싱
    private List<String> parseMoodTags(List<Photo> photos) {
        return photos.stream()
                .map(Photo::getKeywords)
                .filter(Objects::nonNull)
                .flatMap(kw -> Arrays.stream(kw.split("[,#\\s]+")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    // ───────────────────────────────────────────────────────────────────────

    private String buildSummary(List<String> moodTags) {
        if (moodTags.isEmpty()) return "다양한 매력을 가진 여행 취향입니다.";
        String tags = String.join(", ", moodTags.subList(0, Math.min(3, moodTags.size())));
        return tags + " 느낌의 여행을 좋아하시는군요!";
    }

    private RecommendationResponse toResponse(Spot spot, String region) {
        return new RecommendationResponse(
                spot.getSpotId(),
                spot.getName(),
                spot.getRegionName(),
                spot.getImageUrl(),
                region + " 지역이 취향에 맞을 것 같아요"
        );
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
