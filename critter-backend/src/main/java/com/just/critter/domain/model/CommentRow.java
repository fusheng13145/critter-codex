package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentRow {

    private Long id;
    private String bizType;
    private String bizId;
    private Long authorId;
    private String authorName;
    private String content;
    private String status;
    private String replyContent;
    private Boolean replyVisible;
    private LocalDateTime createdAt;
}
