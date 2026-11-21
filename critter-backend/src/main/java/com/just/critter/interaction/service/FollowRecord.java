package com.just.critter.interaction.service;

import java.time.LocalDateTime;

public record FollowRecord(long id, long userId, String bizType, String bizId,
                           LocalDateTime createdAt) {
}
