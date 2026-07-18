package com.sairo.sairo_backend.analysis;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
public class TasteAnalysisController {

    private final TasteAnalysisService tasteAnalysisService;

    @PostMapping("/taste-analysis")
    public TasteAnalysisResponse analyze(@Valid @RequestBody TasteAnalysisRequest request) {
        return tasteAnalysisService.analyze(request.photoIds());
    }

    @GetMapping("/recommendations")
    public RecommendationResponse recommend(@RequestParam String analysisId) {
        return tasteAnalysisService.recommend(analysisId);
    }
}
