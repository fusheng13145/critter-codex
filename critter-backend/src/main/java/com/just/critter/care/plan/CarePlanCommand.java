package com.just.critter.care.plan;

import java.time.LocalDate;

public record CarePlanCommand(
        String animalCode,
        Long visitId,
        String type,
        String itemName,
        String dosage,
        LocalDate planDate,
        String note) {
}
