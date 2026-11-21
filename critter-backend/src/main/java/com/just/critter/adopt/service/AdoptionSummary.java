package com.just.critter.adopt.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdoptionSummary(
        String adoptionNo,
        long userId,
        String userName,
        String animalCode,
        String animalName,
        String planName,
        int amount,
        com.just.critter.adopt.domain.AdoptionPeriod period,
        com.just.critter.adopt.domain.AdoptionStatus status,
        String contactMasked,
        LocalDate startDate,
        LocalDate endDate,
        String certificateNo,
        LocalDateTime createdAt) {
}
