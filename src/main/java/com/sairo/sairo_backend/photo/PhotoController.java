package com.sairo.sairo_backend.photo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            description = """
                    선택 화면에 사용할 사진을 무작위로 반환한다.

                    사용자가 장소를 보고 고르지 않도록 장소명·지역명·분위기 태그는 응답에 포함하지 않는다.
                    """
    )
    @GetMapping
    public List<PhotoResponse> getPhotos(
            @Parameter(description = "반환할 사진 수", example = "40")
            @RequestParam(defaultValue = "40") int limit
    ) {
        return photoRepository.findRandom(limit).stream()
                .map(PhotoResponse::from)
                .collect(Collectors.toList());
    }
}
