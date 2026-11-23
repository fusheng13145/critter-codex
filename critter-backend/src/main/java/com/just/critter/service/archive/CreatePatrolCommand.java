package com.just.critter.service.archive;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 巡查登记命令。
 */
public record CreatePatrolCommand(
        Long pointId,
        OffsetDateTime patrolAt,
        Integer countEstimate,
        Boolean hasAnomaly,
        String anomalyNote,
        List<String> fileIds) {
}
