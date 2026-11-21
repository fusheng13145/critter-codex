package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FollowRow {

    private Long id;
    private Long userId;
    private String bizType;
    private String bizId;
    private LocalDateTime createdAt;
}
