package com.sairo.sairo_backend.place;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "장소 상세", description = "여행지 상세 정보 조회 (DB 우선, 없으면 TourAPI fallback)")
@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @Operation(
        summary = "장소 상세 조회",
        description = "운영시간·휴무일·주차·연락처를 반환합니다. DB에 정보가 없으면 공공 TourAPI에서 보완합니다. infoIncomplete=true면 양쪽 모두 데이터가 없는 경우입니다."
    )
    @GetMapping("/{spotId}")
    public PlaceDetailResponse getPlace(
            @Parameter(description = "스팟 ID", required = true) @PathVariable String spotId) {
        return placeService.getPlace(spotId);
    }
}
