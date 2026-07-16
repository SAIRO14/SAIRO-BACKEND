package com.sairo.sairo_backend.photo;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoRepository photoRepository;

    @GetMapping
    public List<PhotoResponse> getPhotos() {
        return photoRepository.findAll().stream()
                .map(PhotoResponse::from)
                .collect(Collectors.toList());
    }
}
