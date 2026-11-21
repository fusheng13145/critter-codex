package com.just.critter.care.controller;

import java.time.OffsetDateTime;
import java.util.List;

public record VisitCreateRequest(
        Long animalId,
        Long orderId,
        Long clinicId,
        OffsetDateTime visitedAt,
        String complaint,
        String diagnosis,
        String treatment,
        Integer cost,
        List<String> fileIds,
        String sourceNote) {
}
