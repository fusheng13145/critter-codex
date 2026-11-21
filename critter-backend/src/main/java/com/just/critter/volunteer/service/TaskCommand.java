package com.just.critter.volunteer.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskCommand(String title, String type, Long pointId, LocalDateTime startAt,
                          Integer quota, BigDecimal durationHours, String description) {
}
