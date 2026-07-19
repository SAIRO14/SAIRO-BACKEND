package com.sairo.sairo_backend.course;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.sairo.sairo_backend.device.DeviceRepository;

import java.util.List;

@Tag(name = "여행 코스", description = "1박 2일 코스 생성 및 공유 링크 발급")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final CuratedCourseService curatedCourseService;
    private final DeviceRepository deviceRepository;

    @Operation(
        summary = "지역별 관광공사 큐레이션 코스 조회",
        description = "한국관광공사가 편집한 여행코스(contentTypeId=25)를 지역명으로 조회합니다. 최대 3개의 코스를 day1/day2로 나눠 반환합니다. 지원 지역: 제주도, 경상북도, 경상남도, 전라북도, 강원도, 충청남도, 서울특별시"
    )
    @GetMapping("/curated")
    public List<CuratedCourseResponse> getCuratedCourses(
            @Parameter(description = "지역명 (예: 제주도, 경상북도)", required = true)
            @RequestParam String regionName) {
        return curatedCourseService.getCuratedCourses(regionName);
    }

    @Operation(
        summary = "코스 직접 생성 (스팟 ID 지정)",
        description = "선택한 스팟들을 Greedy Nearest-Neighbor 알고리즘으로 정렬 후 절반씩 나눠 day1/day2로 반환합니다. courseId는 서버에 저장되지 않으며 공유 시 path에 사용합니다."
    )
    @PostMapping
    public CourseResponse buildCourse(@Valid @RequestBody CourseRequest request) {
        return courseService.buildCourse(request);
    }

    @Operation(
        summary = "코스 공유 링크 발급",
        description = "코스 데이터를 서버에 저장하고 공유용 shareId와 URL을 반환합니다. 프론트가 POST /courses 응답의 day1/day2를 그대로 body에 담아 호출합니다."
    )
    @PostMapping("/{courseId}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareCourseResponse shareCourse(
            @Parameter(description = "POST /courses에서 받은 courseId") @PathVariable String courseId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestBody ShareCourseRequest request
    ) {
        if (deviceId != null && !deviceId.isBlank()) {
            deviceRepository.upsert(deviceId);
        }
        return courseService.shareCourse(request, deviceId);
    }

    @Operation(
        summary = "공유 코스 조회",
        description = "공유 링크로 진입한 사용자에게 저장된 코스를 보여줍니다."
    )
    @GetMapping("/shared/{shareId}")
    public SharedCourseViewResponse getSharedCourse(
            @Parameter(description = "공유 링크의 shareId", required = true) @PathVariable String shareId) {
        return courseService.getSharedCourse(shareId);
    }
}
