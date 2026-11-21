package com.just.critter.service.archive;

/**
 * 目击列表筛选。groupCode 传空表示全部，assignStatus 用于待归属独立列表。
 */
public record SightingFilter(String groupCode, String assignStatus, Long pointId) {
}
