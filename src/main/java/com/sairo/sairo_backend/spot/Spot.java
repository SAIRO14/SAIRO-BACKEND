package com.sairo.sairo_backend.spot;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "spots")
@Getter
@NoArgsConstructor
public class Spot {

    @Id
    private String spotId;

    private String name;
    private String regionName;
    private Double lat;
    private Double lng;
    private String imageUrl;
    private String operatingHours;
    private String closedDays;
    private String parking;
    private String contact;
    private String cat1;
    private String cat2;
    private String cat3;
}
