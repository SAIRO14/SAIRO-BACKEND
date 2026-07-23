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
                    DB 정보를 우선 사용하고, 부족하면 TourAPI로 보완한다.

                    TourAPI 호출이 실패해도 요청 전체가 실패하지 않는다.
                    보완 후에도 정보가 비어 있으면 `infoIncomplete`가 true로 반환된다.

                    **알려진 문제**: 현재 보완 여부는 운영시간·휴무일·주차·연락처 중
                    **하나라도 있으면 완전하다고 판단**한다. 따라서 운영시간만 있고 나머지가
                    비어 있어도 TourAPI를 호출하지 않는다. 결측 필드별 보완으로 바꿀 예정이다.
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
