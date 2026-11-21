package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TnrTaskRow {
    private String tnrNo;
    private Long animalId;
    private String animalCode;
    private String animalName;
    private String stage;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime occurredAt;
    private String fileIds;
    private String note;
    private LocalDateTime createdAt;
}
