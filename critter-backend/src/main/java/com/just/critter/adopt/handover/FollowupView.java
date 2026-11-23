package com.just.critter.adopt.handover;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FollowupView(
        long id,
        int seqNo,
        LocalDate dueDate,
        String status,
        String result,
        String note,
        LocalDateTime completedAt,
        boolean overdue) {
}
