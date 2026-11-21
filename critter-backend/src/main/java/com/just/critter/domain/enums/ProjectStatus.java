package com.just.critter.domain.enums;

/**
 * 募捐项目状态（需求 §8.3），已结项与已结束为终态。
 */
public enum ProjectStatus {
    DRAFT,
    PENDING_AUDIT,
    FUNDING,
    PENDING_SETTLE,
    SETTLED,
    ENDED,
    REJECTED
}
