package com.sairo.sairo_backend.place;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping("/{spotId}")
    public PlaceDetailResponse getPlace(@PathVariable String spotId) {
        return placeService.getPlace(spotId);
    }
}
