package com.sairo.sairo_backend.photo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "사진 풀", description = "온보딩 취향 선택 화면에 노출할 사진 목록 조회")
@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoRepository photoRepository;

    @Operation(summary = "랜덤 사진 목록 조회", description = "취향 분석에 사용할 사진을 랜덤으로 반환합니다. 사용자는 이 중 최소 5장을 선택합니다.")
    @GetMapping
    public List<PhotoResponse> getPhotos(
            @Parameter(description = "반환할 사진 수 (기본 40장)") @RequestParam(defaultValue = "40") int limit) {
        return photoRepository.findRandom(limit).stream()
                .map(PhotoResponse::from)
                .collect(Collectors.toList());
    }
}
