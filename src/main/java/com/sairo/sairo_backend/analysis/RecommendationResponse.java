package com.sairo.sairo_backend.analysis;

public record RecommendationResponse(
        String spotId,
        String name,
        String regionName,
        String imageUrl,
        String reason
) {}
