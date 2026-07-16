package com.sairo.sairo_backend.photo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "photos")
@Getter
@NoArgsConstructor
public class Photo {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String imageUrl;

    private String location;

    private String keywords;
    // embedding 컬럼은 JPA 매핑 제외 — PhotoEmbeddingRepository에서 native SQL로 처리
}
