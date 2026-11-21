package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TnrNodeRow {
    private String stage;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime occurredAt;
    private String note;
    private String fileIds;
}
