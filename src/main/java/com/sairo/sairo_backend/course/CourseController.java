package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.common.DeviceId;
import com.sairo.sairo_backend.common.IdFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
                    장소 목록을 좌표 기준으로 정렬해 Day 1과 Day 2로 나누고, 결과를 저장한 뒤 `courseId`를 발급한다.

                    좌표가 없는 장소는 뒤로 배치한다.

                    모든 장소의 지역이 같아야 하고, 그 지역이 요청한 `regionName`을 포함해야 한다.
                    저장되는 지역명은 요청 값이 아니라 **장소에서 유도한 값**이다.
                    (`regionName: "제주"` + 장소 지역 `"제주도"` → 스냅샷에는 `"제주도"`)

                    요청한 기기가 코스의 소유자가 된다. 발급된 `courseId`로 공유 스냅샷을 만들 수 있고,
                    저장 목록에 담을 수 있다. **둘 다 소유자만 가능하다.**
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST / INSUFFICIENT_SPOTS — 유효한 장소가 2개 미만 / COURSE_REGION_MISMATCH — 요청 지역과 장소의 지역이 다름")
    })
    @PostMapping
    public CourseResponse buildCourse(
            @DeviceId String deviceId,
            @Valid @RequestBody CourseRequest request
    ) {
        return courseService.buildCourse(deviceId, request);
    }

    @Operation(
            summary = "코스 조회",
            description = """
                    저장된 코스의 지역과 Day 1·Day 2를 반환한다.

                    **자기가 만든 코스만 조회할 수 있다.** 남의 코스는 없는 것과 같게 404다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "COURSE_NOT_FOUND — 해당 코스가 없거나 다른 기기의 코스임"),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR — 코스 스냅샷 역직렬화 실패")
    })
    @GetMapping("/{courseId}")
    public CourseResponse getCourse(
            @DeviceId String deviceId,
            @Parameter(description = "조회할 코스 ID", required = true, example = "8f14e45f-ea8d-4f4a-9c1b-2c3d4e5f6a7b")
            @PathVariable String courseId
    ) {
        return courseService.getCourse(deviceId, courseId);
    }

    @Operation(
            summary = "코스 공유 스냅샷 생성",
            description = """
                    `POST /courses`가 저장해둔 코스를 읽어 읽기 전용 스냅샷으로 남기고 공유 링크를 반환한다.
                    스냅샷에는 지역과 Day 1·Day 2가 함께 들어간다.

                    요청 본문은 받지 않는다. 공유할 내용은 서버가 저장한 코스에서만 가져온다.

                    같은 코스를 여러 번 공유해도 링크는 하나다. 중복 요청에는 같은 `shareId`를 반환한다.

                    **자기가 만든 코스만 공유할 수 있다.** 남의 코스는 없는 것과 같게 404다.
                    만들어진 공유 링크의 조회는 그대로 공개다.

                    `courseId`는 소문자 UUID v4여야 한다. 형식이 다르면 404가 아니라 400이다.
                    서버가 발급한 적 없는 형식을 "없는 코스"라고 답하면, 실재하는 자기 코스를
                    두고 없다고 말하게 된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "공유 스냅샷 생성됨 (이미 공유된 코스면 기존 링크)"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST — courseId가 소문자 UUID v4 아님"),
            @ApiResponse(responseCode = "404", description = "COURSE_NOT_FOUND — 해당 코스가 없거나 다른 기기의 코스임"),
            @ApiResponse(responseCode = "500", description = "SHARE_CREATION_FAILED — 스냅샷 저장 실패")
    })
    @PostMapping("/{courseId}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareCourseResponse shareCourse(
            @DeviceId String deviceId,
            @Parameter(description = "공유할 코스 ID", required = true, example = "8f14e45f-ea8d-4f4a-9c1b-2c3d4e5f6a7b")
            // 경로에 있지만 형식을 검증한다. 대문자 UUID를 통과시키면 courses.course_id가 TEXT라
            // 조회가 빗나가, 실재하는 자기 코스에 COURSE_NOT_FOUND 404가 나간다.
            // 계약 §2의 기준은 경로냐 본문이냐가 아니라 "틀린 형식이 거짓 부재를 만드는가"다.
            @PathVariable
            @Pattern(regexp = IdFormat.UUID_V4, message = "courseId는 소문자 UUID v4 정규형이어야 합니다.")
            String courseId
    ) {
        return courseService.shareCourse(deviceId, courseId);
    }

    @Operation(
            summary = "공유 코스 조회",
            description = """
                    공유 당시 스냅샷을 그대로 반환한다. 지역과 Day 1·Day 2를 포함한다.

                    읽기 전용이며 편집과 삭제를 제공하지 않는다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SHARED_COURSE_NOT_FOUND — 공유 ID가 없거나 만료됨"),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR — 공유 스냅샷 역직렬화 실패")
    })
    @GetMapping("/shared/{shareId}")
    public SharedCourseViewResponse getSharedCourse(
            @Parameter(description = "공유 링크에 포함된 ID", required = true)
            @PathVariable String shareId
    ) {
        return courseService.getSharedCourse(shareId);
    }
}
