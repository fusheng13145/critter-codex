package com.just.critter.domain.enums;

/**
 * 救助工单状态迁移事件（需求 §8.4）。
 */
public enum OrderEvent {
    ACCEPT,
    DISPOSE_DONE,
    CANNOT_DISPOSE,
    REVISIT_OK,
    REVISIT_ANOMALY,
    TIMEOUT
}
