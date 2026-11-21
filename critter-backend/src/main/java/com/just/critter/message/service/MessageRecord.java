package com.just.critter.message.service;

import java.time.LocalDateTime;

public record MessageRecord(
        long id,
        String type,
        String title,
        String bizType,
        String bizId,
        boolean readFlag,
        LocalDateTime readAt,
        LocalDateTime createdAt) {
}
