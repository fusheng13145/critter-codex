package com.just.critter.interaction.service;

import java.time.LocalDateTime;

public record FollowView(long followId, String bizType, String bizId, LocalDateTime createdAt,
                         boolean newlyCreated) {
}
