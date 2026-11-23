package com.just.critter.adopt.handover;

import java.time.LocalDateTime;

public record HandoverCommand(
        String animalCode,
        String adopterName,
        String contact,
        Boolean guardianConsent,
        String residenceNote,
        LocalDateTime adoptedAt) {
}
