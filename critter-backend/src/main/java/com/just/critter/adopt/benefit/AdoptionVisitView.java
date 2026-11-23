package com.just.critter.adopt.benefit;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdoptionVisitView(
        long id,
        LocalDate visitDate,
        String timeSlot,
        String note,
        String status,
        String reviewReason,
        LocalDateTime createdAt) {
}
