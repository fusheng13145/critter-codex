package com.just.critter.volunteer.shift;

import java.time.LocalDateTime;

public record SuspensionView(
        String status,
        int absenceStreak,
        LocalDateTime suspendedAt,
        boolean canAppeal,
        boolean pendingAppeal) {
}
