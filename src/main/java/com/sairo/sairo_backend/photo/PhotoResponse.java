package com.sairo.sairo_backend.photo;

public record PhotoResponse(
        String id,
        String imageUrl,
        String location
) {
    static PhotoResponse from(Photo photo) {
        return new PhotoResponse(photo.getId(), photo.getImageUrl(), photo.getLocation());
    }
}
