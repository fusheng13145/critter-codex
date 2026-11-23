package com.just.critter.controller.archive;

import com.just.critter.service.archive.CreatePatrolCommand;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 巡查登记请求。
 */
public record CreatePatrolRequest(
        Long pointId,
        OffsetDateTime patrolAt,
        Integer countEstimate,
        Boolean hasAnomaly,
        String anomalyNote,
        List<String> fileIds) {

    public CreatePatrolCommand toCommand() {
        return new CreatePatrolCommand(pointId, patrolAt, countEstimate, hasAnomaly, anomalyNote, fileIds);
    }
}
