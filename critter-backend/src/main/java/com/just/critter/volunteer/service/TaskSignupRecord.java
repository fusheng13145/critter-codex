package com.just.critter.volunteer.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskSignupRecord(long id, long taskId, long userId, LocalDateTime signedAt,
                               LocalDateTime checkinAt, String checkinPhoto,
                               BigDecimal hours, boolean absent) {
}
