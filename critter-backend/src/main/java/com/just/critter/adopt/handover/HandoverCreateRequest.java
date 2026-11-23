package com.just.critter.adopt.handover;

import java.time.LocalDateTime;

public record HandoverCreateRequest(
        String animalCode,
        String adopterName,
        String contact,
        Boolean guardianConsent,
        String residenceNote,
        LocalDateTime adoptedAt) {

    public HandoverCommand toCommand() {
        return new HandoverCommand(animalCode, adopterName, contact, guardianConsent, residenceNote, adoptedAt);
    }
}
