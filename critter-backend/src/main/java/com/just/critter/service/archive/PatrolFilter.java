package com.just.critter.service.archive;

import java.time.OffsetDateTime;

/**
 * 巡查列表筛选。
 */
public record PatrolFilter(Long pointId, OffsetDateTime from, OffsetDateTime to) {
}
