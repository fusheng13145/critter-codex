package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VisitDetailRow {
    private String visitNo;
    private Long animalId;
    private String animalCode;
    private String animalName;
    private Long orderId;
    private String orderNo;
    private Long clinicId;
    private String clinicName;
    private LocalDateTime visitedAt;
    private String complaint;
    private String diagnosis;
    private String treatment;
    private Integer cost;
    private String fileIds;
    private String sourceNote;
    private LocalDateTime createdAt;
}
