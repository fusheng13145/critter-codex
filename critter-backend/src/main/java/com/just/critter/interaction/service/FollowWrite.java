package com.just.critter.interaction.service;

import java.time.LocalDateTime;

public record FollowWrite(long userId, String bizType, String bizId,
                          LocalDateTime createdAt, long createdBy) {
}
