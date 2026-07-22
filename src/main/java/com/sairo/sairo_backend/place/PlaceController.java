package com.sairo.sairo_backend.place;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "장소", description = "코스에 포함된 개별 장소의 방문 정보")
@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @Operation(
            summary = "장소 상세 조회",
            description = """
                    DB 정보를 우선 사용하고, 부족한 항목은 TourAPI로 보완한다.

                    TourAPI 호출이 실패해도 요청 전체가 실패하지 않는다.
                    보완 후에도 정보가 비어 있으면 `infoIncomplete`가 true로 반환된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND — 장소 없음")
    })
    @GetMapping("/{spotId}")
    public PlaceDetailResponse getPlace(
            @Parameter(description = "장소 ID", example = "126508")
            @PathVariable String spotId
    ) {
        return placeService.getPlace(spotId);
    }
}
