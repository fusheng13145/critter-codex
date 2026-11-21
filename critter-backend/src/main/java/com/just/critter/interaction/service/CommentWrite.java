package com.just.critter.interaction.service;

import java.time.LocalDateTime;

public record CommentWrite(String bizType, String bizId, long authorId, String content,
                           CommentStatus status, LocalDateTime createdAt, long createdBy) {
}
