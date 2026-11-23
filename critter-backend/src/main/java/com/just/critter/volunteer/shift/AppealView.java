package com.just.critter.volunteer.shift;

import java.time.LocalDateTime;

public record AppealView(
        long id,
        String userName,
        String reason,
        String status,
        String reviewReason,
        LocalDateTime decidedAt,
        LocalDateTime createdAt) {
}
