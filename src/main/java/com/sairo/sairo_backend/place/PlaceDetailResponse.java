package com.sairo.sairo_backend.place;

import com.sairo.sairo_backend.spot.Spot;

public record PlaceDetailResponse(
        String spotId,
        String name,
        Double lat,
        Double lng,
        String imageUrl,
        String operatingHours,
        String closedDays,
        String parking,
        String contact,
        boolean infoIncomplete
) {
    static PlaceDetailResponse from(Spot spot, boolean infoIncomplete) {
        return new PlaceDetailResponse(
                spot.getSpotId(),
                spot.getName(),
                spot.getLat(),
                spot.getLng(),
                spot.getImageUrl(),
                spot.getOperatingHours(),
                spot.getClosedDays(),
                spot.getParking(),
                spot.getContact(),
                infoIncomplete
        );
    }
}
