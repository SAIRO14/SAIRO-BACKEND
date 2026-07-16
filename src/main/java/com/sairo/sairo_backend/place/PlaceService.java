package com.sairo.sairo_backend.place;

import com.sairo.sairo_backend.spot.Spot;
import com.sairo.sairo_backend.spot.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final SpotRepository spotRepository;
    private final TourApiClient tourApiClient;

    public PlaceDetailResponse getPlace(String spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."));

        if (hasCompleteInfo(spot)) {
            return PlaceDetailResponse.from(spot, false);
        }

        // DB 정보 부족 → TourAPI fallback
        return tourApiClient.fetchDetail(spotId)
                .map(detail -> mergeWithTourApi(spot, detail))
                .orElse(PlaceDetailResponse.from(spot, true));
    }

    private boolean hasCompleteInfo(Spot spot) {
        return isPresent(spot.getOperatingHours())
                || isPresent(spot.getClosedDays())
                || isPresent(spot.getParking())
                || isPresent(spot.getContact());
    }

    private PlaceDetailResponse mergeWithTourApi(Spot spot, TourApiClient.TourDetail detail) {
        boolean stillIncomplete = !isPresent(detail.operatingHours())
                && !isPresent(detail.closedDays())
                && !isPresent(detail.parking())
                && !isPresent(detail.contact());

        return new PlaceDetailResponse(
                spot.getSpotId(),
                spot.getName(),
                spot.getLat(),
                spot.getLng(),
                spot.getImageUrl(),
                coalesce(spot.getOperatingHours(), detail.operatingHours()),
                coalesce(spot.getClosedDays(), detail.closedDays()),
                coalesce(spot.getParking(), detail.parking()),
                coalesce(spot.getContact(), detail.contact()),
                stillIncomplete
        );
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private String coalesce(String a, String b) {
        return isPresent(a) ? a : b;
    }
}
