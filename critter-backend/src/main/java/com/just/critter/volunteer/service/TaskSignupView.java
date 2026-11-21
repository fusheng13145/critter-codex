package com.just.critter.volunteer.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskSignupView(long signupId, long taskId, String taskNo, String title,
                             LocalDateTime startAt, LocalDateTime signedAt,
                             LocalDateTime checkinAt, BigDecimal hours, String taskStatus,
                             boolean checkedIn, boolean absent) {
}
