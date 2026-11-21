package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageRow {
    private Long id;
    private Long receiverId;
    private String type;
    private String title;
    private String bizType;
    private String bizId;
    private Boolean readFlag;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
