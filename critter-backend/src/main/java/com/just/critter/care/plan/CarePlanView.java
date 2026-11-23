package com.just.critter.care.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CarePlanView(
        long id,
        String planNo,
        String animalCode,
        String type,
        String itemName,
        String dosage,
        LocalDate planDate,
        String status,
        Long sourceVisitId,
        String result,
        LocalDateTime completedAt,
        String note,
        boolean overdue) {
}
