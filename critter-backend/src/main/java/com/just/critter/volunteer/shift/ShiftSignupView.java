package com.just.critter.volunteer.shift;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShiftSignupView(
        long id,
        long userId,
        String userName,
        LocalDateTime signedAt,
        LocalDateTime checkinAt,
        boolean absent,
        BigDecimal hours) {
}
