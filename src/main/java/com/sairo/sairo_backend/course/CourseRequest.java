package com.sairo.sairo_backend.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CourseRequest(
        // 코스 스냅샷에 그대로 들어가므로 비어 있으면 안 된다.
        @NotBlank
        String regionName,
        @NotEmpty @Size(min = 2, max = 20)
        List<String> spotIds
) {}
