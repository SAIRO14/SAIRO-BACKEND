package com.sairo.sairo_backend.photo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "사진", description = "취향 분석용 사진 풀 조회")
@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoRepository photoRepository;

    @Operation(
            summary = "사진 풀 조회",
            description = "선택 화면에 사용할 사진을 무작위로 반환한다. 응답에는 ID와 이미지 URL만 포함한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST — limit이 허용 범위를 벗어남")
    })
    @GetMapping
    public List<PhotoResponse> getPhotos(
            @Parameter(description = "반환할 사진 수 (1~100)", example = "40")
            @RequestParam(defaultValue = "40") @Min(1) @Max(100) int limit
    ) {
        return photoRepository.findRandom(limit).stream()
                .map(PhotoResponse::from)
                .collect(Collectors.toList());
    }
}
