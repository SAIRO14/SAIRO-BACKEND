package com.sairo.sairo_backend.analysis;

import com.sairo.sairo_backend.common.DeviceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 두 엔드포인트의 최상위 경로가 서로 달라({@code /taste-analysis}, {@code /recommendations})
 * 클래스 레벨 {@code @RequestMapping}을 두지 않는다. 다른 컨트롤러와 형태가 다른 이유다.
 */
@Tag(name = "취향 분석", description = "선택한 사진의 분위기 분석과 지역 추천")
@RestController
@RequiredArgsConstructor
public class TasteAnalysisController {

    private final TasteAnalysisService tasteAnalysisService;

    @Operation(
            summary = "취향 분석 및 코스 생성",
            description = """
                    선택한 사진의 임베딩 평균과 분위기 태그를 계산하고,
                    취향과 가까운 상위 지역의 코스를 즉시 생성·저장한다.

                    분석과 코스 생성이 단일 요청으로 완료된다.
                    각 코스에 `courseId`가 발급되어 저장·공유에 바로 사용 가능하다.

                    장소가 2개 미만인 지역은 코스에서 제외된다. 0개도 정상 응답이다.
                    `regionArea`는 클러스터 풀 기준으로 계산하며 null일 수 있다.
                    `saved`는 항상 false다. 저장 여행지에 추가한 시점부터 true가 된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분석 및 코스 생성 성공"),
            @ApiResponse(responseCode = "400", description = """
                    INVALID_REQUEST — 개수 범위(5~10장) 위반 / \
                    INVALID_PHOTO_SELECTION — 중복 제거 후 5장 미만 또는 유효한 사진이 5장 미만""")
    })
    @PostMapping("/taste-analysis")
    public TasteAnalysisResponse analyze(
            @DeviceId String deviceId,
            @Valid @RequestBody TasteAnalysisRequest request
    ) {
        return tasteAnalysisService.analyzeAndBuildCourses(request.photoIds(), deviceId);
    }

    @Operation(
            summary = "추천 조회 (deprecated)",
            description = """
                    분석 ID로 계산된 취향과 가까운 지역 카드를 반환한다.

                    `POST /taste-analysis`가 코스를 직접 반환하므로 이 엔드포인트는 더 이상 사용되지 않는다.
                    분석 ID를 얻을 수 있는 경로가 없으므로 사실상 호출 불가 상태다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST — analysisId 누락"),
            @ApiResponse(responseCode = "404", description = "ANALYSIS_NOT_FOUND — 분석 ID가 없거나 만료됨")
    })
    @GetMapping("/recommendations")
    public RecommendationResponse recommend(
            @Parameter(description = "취향 분석 API가 발급한 분석 ID", required = true)
            @RequestParam String analysisId,
            @DeviceId Optional<String> deviceId
    ) {
        return tasteAnalysisService.recommend(analysisId);
    }
}
