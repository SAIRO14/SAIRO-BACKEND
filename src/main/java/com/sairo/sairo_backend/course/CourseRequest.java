package com.sairo.sairo_backend.course;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CourseRequest(
        String regionName,
        @NotEmpty @Size(min = 2, max = 20)
        List<String> spotIds
) {}
