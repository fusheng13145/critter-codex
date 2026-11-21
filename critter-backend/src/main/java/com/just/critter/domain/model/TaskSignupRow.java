package com.just.critter.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TaskSignupRow {

    private Long id;
    private Long taskId;
    private Long userId;
    private String taskNo;
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime signedAt;
    private LocalDateTime checkinAt;
    private String checkinPhoto;
    private BigDecimal hours;
    private String taskStatus;
    private Boolean absent;
}
