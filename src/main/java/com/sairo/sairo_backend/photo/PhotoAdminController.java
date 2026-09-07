package com.sairo.sairo_backend.photo;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/photos")
@RequiredArgsConstructor
public class PhotoAdminController {

    private final PhotoRepository photoRepository;

    @GetMapping
    public PagedPhotoResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "48") int size
    ) {
        Page<Photo> result = photoRepository.findAll(
                PageRequest.of(page, size, Sort.by("id")));
        return new PagedPhotoResponse(
                result.getContent().stream().map(PhotoAdminItem::from).toList(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.getNumber()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!photoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        photoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    record PhotoAdminItem(String id, String title, String imageUrl, String location, String keywords) {
        static PhotoAdminItem from(Photo p) {
            return new PhotoAdminItem(p.getId(), p.getTitle(), p.getImageUrl(), p.getLocation(), p.getKeywords());
        }
    }

    record PagedPhotoResponse(
            java.util.List<PhotoAdminItem> photos,
            int totalPages,
            long totalElements,
            int currentPage
    ) {}
}
