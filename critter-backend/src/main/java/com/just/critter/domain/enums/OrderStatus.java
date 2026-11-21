package com.just.critter.domain.enums;

/**
 * 救助工单状态（需求 §8.4）。
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    TO_REVISIT,
    TIMEOUT,
    CLOSED
}
