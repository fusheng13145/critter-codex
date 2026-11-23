package com.just.critter.care.plan;

import java.time.LocalDate;

public record CarePlanCreateRequest(
        String animalCode,
        Long visitId,
        String type,
        String itemName,
        String dosage,
        LocalDate planDate,
        String note) {

    public CarePlanCommand toCommand() {
        return new CarePlanCommand(animalCode, visitId, type, itemName, dosage, planDate, note);
    }
}
