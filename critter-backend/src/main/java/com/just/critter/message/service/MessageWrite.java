package com.just.critter.message.service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 消息写入命令。标题按 message.title 的 VARCHAR(128) 截断，避免长审核理由导致整笔事务失败。
 */
public record MessageWrite(
        long receiverId,
        String type,
        String title,
        String bizType,
        String bizId,
        Long createdBy,
        LocalDateTime createdAt) {

    private static final int TITLE_MAX_LENGTH = 128;

    public MessageWrite {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        if (title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
        }
    }
}
