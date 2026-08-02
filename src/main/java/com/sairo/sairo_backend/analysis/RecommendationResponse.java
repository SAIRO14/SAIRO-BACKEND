package com.sairo.sairo_backend.analysis;

import java.util.List;

public record RecommendationResponse(
        List<String> moodTags,
        List<RegionCard> regions
) {
    public record RegionCard(
            String regionId,
            String regionName,
            String regionArea,
            String imageUrl,
            String reason,
            boolean saved,
            List<PreviewSpot> previewSpots
    ) {}

    public record PreviewSpot(
            String spotId,
            String name
    ) {}
}
