package com.just.critter.adopt.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdoptionTermination(
        long adoptionId,
        String reason,
        LocalDate endDate,
        LocalDateTime updatedAt) {
}
