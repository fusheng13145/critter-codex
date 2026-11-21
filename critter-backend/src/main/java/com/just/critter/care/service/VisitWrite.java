package com.just.critter.care.service;

import java.time.LocalDateTime;
import java.util.List;

public record VisitWrite(
        String visitNo,
        long animalId,
        Long orderId,
        long clinicId,
        LocalDateTime visitedAt,
        String complaint,
        String diagnosis,
        String treatment,
        int cost,
        List<String> fileIds,
        String sourceNote,
        LocalDateTime createdAt,
        long createdBy) {
}
