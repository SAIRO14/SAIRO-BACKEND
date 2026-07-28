package com.sairo.sairo_backend.analysis;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TasteAnalysisRequest(
        @NotEmpty @Size(min = 5, max = 10)
        List<String> photoIds
) {}
