package com.sairo.sairo_backend.course;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "코스", description = "1박 2일 코스 생성과 공유")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(
            summary = "코스 생성",
            description = """
                    장소 목록을 좌표 기준으로 정렬해 Day 1과 Day 2로 나눈다.

                    좌표가 없는 장소는 뒤로 배치한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST / INSUFFICIENT_SPOTS — 유효한 장소가 2개 미만")
    })
    @PostMapping
    public CourseResponse buildCourse(@Valid @RequestBody CourseRequest request) {
        return courseService.buildCourse(request);
    }

    @Operation(
            summary = "코스 공유 스냅샷 생성",
            description = """
                    공유 시점의 코스를 읽기 전용 스냅샷으로 저장하고 공유 링크를 반환한다.

                    **알려진 문제**: 현재 경로의 `courseId`는 사용되지 않고 요청 본문이 그대로 저장된다.
                    지역 정보도 저장되지 않는다. 서버가 생성한 코스와 연결하도록 바꿔야 한다.
                    (docs/open-questions.md Q-03)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "공유 스냅샷 생성됨"),
            @ApiResponse(responseCode = "500", description = "SHARE_CREATION_FAILED — 스냅샷 저장 실패")
    })
    @PostMapping("/{courseId}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareCourseResponse shareCourse(
            @Parameter(description = "공유할 코스 ID", required = true)
            @PathVariable String courseId,
            @Valid @RequestBody ShareCourseRequest request
    ) {
        return courseService.shareCourse(request);
    }

    @Operation(
            summary = "공유 코스 조회",
            description = "공유 당시 스냅샷을 그대로 반환한다. 읽기 전용이며 편집과 삭제를 제공하지 않는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SHARED_COURSE_NOT_FOUND — 공유 ID가 없거나 만료됨")
    })
    @GetMapping("/shared/{shareId}")
    public SharedCourseViewResponse getSharedCourse(
            @Parameter(description = "공유 링크에 포함된 ID", required = true)
            @PathVariable String shareId
    ) {
        return courseService.getSharedCourse(shareId);
    }
}
