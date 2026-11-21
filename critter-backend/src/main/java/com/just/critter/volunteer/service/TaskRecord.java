package com.just.critter.volunteer.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskRecord(long id, String taskNo, String title, String type, String description,
                         Long pointId, String pointName, LocalDateTime startAt, int quota,
                         int joined, BigDecimal durationHours, String status,
                         LocalDateTime signedAt, LocalDateTime checkinAt, boolean absent) {
}
