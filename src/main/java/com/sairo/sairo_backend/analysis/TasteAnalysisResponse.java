package com.sairo.sairo_backend.analysis;

import java.util.List;

public record TasteAnalysisResponse(
        String analysisId,
        List<String> moodTags,
        String summary
) {}
