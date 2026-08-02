package com.sairo.sairo_backend.course;

import com.sairo.sairo_backend.spot.Spot;

public record SpotSummary(
        String spotId,
        String name,
        Double lat,
        Double lng,
        String imageUrl,
        String operatingHours,
        String closedDays,
        String parking,
        String contact
) {
    static SpotSummary from(Spot spot) {
        return new SpotSummary(
                spot.getSpotId(),
                spot.getName(),
                spot.getLat(),
                spot.getLng(),
                spot.getImageUrl(),
                spot.getOperatingHours(),
                spot.getClosedDays(),
                spot.getParking(),
                spot.getContact()
        );
    }
}
