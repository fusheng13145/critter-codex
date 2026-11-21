package com.just.critter.service.archive;

import com.just.critter.domain.enums.GroupStatus;

/**
 * 群体列表筛选；pointId 走 JSON_CONTAINS。
 */
public record GroupFilter(String keyword, Long speciesId, GroupStatus status, Long pointId) {
}
