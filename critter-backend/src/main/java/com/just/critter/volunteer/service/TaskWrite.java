package com.just.critter.volunteer.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskWrite(String taskNo, String title, String type, String description,
                        Long pointId, LocalDateTime startAt, int quota,
                        BigDecimal durationHours, String status, LocalDateTime createdAt,
                        long createdBy) {
}
