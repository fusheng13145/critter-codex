package com.just.critter.service.archive;

/**
 * 物种名录筛选；关键字同时匹配常见名与学名。
 */
public record SpeciesFilter(String keyword, String category) {
}
