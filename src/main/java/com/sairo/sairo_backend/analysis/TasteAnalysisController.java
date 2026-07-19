package com.sairo.sairo_backend.analysis;

import com.sairo.sairo_backend.device.DeviceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "취향 분석 & 추천", description = "선택한 사진으로 취향을 분석하고 여행지를 추천합니다")
@RestController
@RequiredArgsConstructor
public class TasteAnalysisController {

    private final TasteAnalysisService tasteAnalysisService;
    private final DeviceRepository deviceRepository;

    @Operation(
        summary = "취향 분석",
        description = "선택한 사진들의 임베딩 평균으로 취향 벡터를 생성합니다. 반환된 analysisId는 추천 조회 시 사용합니다. X-Device-Id 헤더를 포함하면 분석 이력이 저장됩니다."
    )
    @PostMapping("/taste-analysis")
    public TasteAnalysisResponse analyze(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @Valid @RequestBody TasteAnalysisRequest request) {
        TasteAnalysisResponse response = tasteAnalysisService.analyze(request.photoIds());
        if (deviceId != null && !deviceId.isBlank()) {
            deviceRepository.upsert(deviceId);
            deviceRepository.saveAnalysisHistory(deviceId, response.analysisId(),
                    String.join(",", response.moodTags()));
        }
        return response;
    }

    @Operation(
        summary = "여행지 추천 조회",
        description = "취향 벡터와 유사한 사진의 지역을 기반으로 스팟을 추천합니다. spots를 regionName으로 그룹핑하면 지역 카드 리스트가 됩니다."
    )
    @GetMapping("/recommendations")
    public RecommendationResponse recommend(
            @Parameter(description = "POST /taste-analysis에서 발급된 analysisId", required = true)
            @RequestParam String analysisId) {
        return tasteAnalysisService.recommend(analysisId);
    }
}
