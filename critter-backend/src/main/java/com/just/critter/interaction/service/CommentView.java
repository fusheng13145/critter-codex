package com.just.critter.interaction.service;

import java.time.LocalDateTime;

public record CommentView(long commentId, String bizType, String bizId, long authorId,
                          String authorName, String content, CommentStatus status,
                          String replyContent, boolean replyVisible, LocalDateTime createdAt) {
}
