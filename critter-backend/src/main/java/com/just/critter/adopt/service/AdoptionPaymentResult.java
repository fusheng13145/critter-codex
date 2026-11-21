package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionStatus;

import java.time.LocalDate;

public record AdoptionPaymentResult(
        String adoptionNo,
        AdoptionStatus status,
        LocalDate startDate,
        LocalDate endDate,
        String certificateNo,
        String animalStatus) {
}
