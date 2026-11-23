package com.just.critter.adopt.handover;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HandoverView(
        long id,
        String handoverNo,
        long animalId,
        String animalCode,
        String adopterName,
        String contact,
        boolean guardianConsent,
        String residenceNote,
        String status,
        LocalDateTime adoptedAt,
        int completedFollowups,
        int totalFollowups) {
}
