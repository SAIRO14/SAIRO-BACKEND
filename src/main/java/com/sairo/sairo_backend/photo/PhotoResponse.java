package com.sairo.sairo_backend.photo;

public record PhotoResponse(
        String id,
        String imageUrl
) {
    static PhotoResponse from(Photo photo) {
        return new PhotoResponse(photo.getId(), photo.getImageUrl());
    }
}
