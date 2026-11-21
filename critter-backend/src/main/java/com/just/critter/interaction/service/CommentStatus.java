package com.just.critter.interaction.service;

public enum CommentStatus {
    VISIBLE,
    PENDING_AUDIT;

    public static CommentStatus parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("留言状态为空");
        }
        return CommentStatus.valueOf(value);
    }
}
