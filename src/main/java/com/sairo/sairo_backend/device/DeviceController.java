package com.sairo.sairo_backend.device;

import com.sairo.sairo_backend.course.ShareCourseRequest;
import com.sairo.sairo_backend.course.SharedCourseRepository;
import com.sairo.sairo_backend.course.SpotSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "디바이스", description = "디바이스 식별자 기반 분석 이력 및 저장 코스 조회")
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final SharedCourseRepository sharedCourseRepository;
    private final ObjectMapper objectMapper;

    @Operation(
        summary = "취향 분석 이력 조회",
        description = "X-Device-Id로 식별된 디바이스의 취향 분석 이력을 최신순으로 반환합니다."
    )
    @GetMapping("/{deviceId}/history")
    public List<AnalysisHistoryResponse> getAnalysisHistory(
            @Parameter(description = "앱 최초 실행 시 생성된 UUID", required = true)
            @PathVariable String deviceId) {
        return deviceRepository.findAnalysisHistory(deviceId).stream()
                .map(item -> new AnalysisHistoryResponse(
                        item.analysisId(),
                        item.moodTags() != null ? Arrays.asList(item.moodTags().split(",")) : List.of(),
                        item.createdAt()
                ))
                .collect(Collectors.toList());
    }

    @Operation(
        summary = "저장 코스 이력 조회",
        description = "X-Device-Id로 식별된 디바이스에서 공유/저장한 코스 목록을 최신순으로 반환합니다."
    )
    @GetMapping("/{deviceId}/courses")
    public List<SavedCourseResponse> getSavedCourses(
            @Parameter(description = "앱 최초 실행 시 생성된 UUID", required = true)
            @PathVariable String deviceId) {
        return sharedCourseRepository.findByDeviceId(deviceId).stream()
                .map(item -> {
                    List<SpotSummary> day1 = List.of();
                    List<SpotSummary> day2 = List.of();
                    try {
                        ShareCourseRequest data = objectMapper.readValue(item.courseDataJson(), ShareCourseRequest.class);
                        day1 = data.day1();
                        day2 = data.day2();
                    } catch (Exception ignored) {}
                    return new SavedCourseResponse(item.shareId(), day1, day2, item.createdAt());
                })
                .collect(Collectors.toList());
    }

    public record AnalysisHistoryResponse(
            String analysisId,
            List<String> moodTags,
            LocalDateTime createdAt
    ) {}

    public record SavedCourseResponse(
            String shareId,
            List<SpotSummary> day1,
            List<SpotSummary> day2,
            LocalDateTime createdAt
    ) {}
}
