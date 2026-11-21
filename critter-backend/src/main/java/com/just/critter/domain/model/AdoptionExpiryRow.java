package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdoptionExpiryRow {
    private Long id;
    private String adoptionNo;
    private Long userId;
    private Long animalId;
    private String animalCode;
    private LocalDate endDate;
}
