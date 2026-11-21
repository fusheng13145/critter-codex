package com.just.critter.adopt.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdoptionActivation(
        long adoptionId,
        String certificateNo,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime updatedAt) {
}
