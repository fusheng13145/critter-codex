package com.just.critter.domain.enums;

/**
 * 群体档案状态迁移事件（需求 §8.5）。
 */
public enum GroupEvent {
    NO_SIGHTING_60D,
    SIGHTING_CONFIRMED,
    CONFIRM_GONE
}
