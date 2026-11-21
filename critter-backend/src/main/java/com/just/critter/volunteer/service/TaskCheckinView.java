package com.just.critter.volunteer.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskCheckinView(long taskId, String taskNo, String title,
                              LocalDateTime checkinAt, BigDecimal hours,
                              BigDecimal totalHours) {
}
