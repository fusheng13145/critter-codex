package com.just.critter.volunteer.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskCreateRequest(String title, String type, Long pointId,
                                LocalDateTime startAt, Integer quota,
                                BigDecimal durationHours, String description) {
}
