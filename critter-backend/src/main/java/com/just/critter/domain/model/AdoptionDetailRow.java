package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdoptionDetailRow {
    private Long id;
    private String adoptionNo;
    private Long userId;
    private String userName;
    private String animalCode;
    private String animalName;
    private Long planId;
    private String planName;
    private Integer amount;
    private String period;
    private String status;
    private String contact;
    private String auditReason;
    private LocalDate startDate;
    private LocalDate endDate;
    private String certificateNo;
    private LocalDateTime payDeadline;
    private LocalDateTime createdAt;
}
