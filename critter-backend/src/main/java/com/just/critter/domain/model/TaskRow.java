package com.just.critter.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TaskRow {

    private Long id;
    private String taskNo;
    private String title;
    private String type;
    private String description;
    private Long pointId;
    private String pointName;
    private LocalDateTime startAt;
    private Integer quota;
    private Integer joined;
    private BigDecimal durationHours;
    private String status;
    private LocalDateTime signedAt;
    private LocalDateTime checkinAt;
    private Boolean absent;
}
