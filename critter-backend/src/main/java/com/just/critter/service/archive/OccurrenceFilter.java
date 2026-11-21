package com.just.critter.service.archive;

import com.just.critter.domain.enums.Season;

/**
 * 出现记录筛选；season 只换算成月份条件，不落库。
 */
public record OccurrenceFilter(Long speciesId, Long pointId, Season season, String keyword) {
}
