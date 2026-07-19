package com.sairo.sairo_backend.analysis;

import java.util.List;

public record RecommendationResponse(
        List<String> moodTags,
        List<SpotResult> spots
) {
    public record SpotResult(
            String spotId,
            String name,
            String regionName,
            Double lat,
            Double lng,
            String imageUrl
    ) {}
}
