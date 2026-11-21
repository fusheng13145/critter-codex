package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeRow {
    private Long id;
    private String noticeNo;
    private String category;
    private String title;
    private String content;
    private Long authorId;
    private String authorName;
    private Boolean pinned;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
