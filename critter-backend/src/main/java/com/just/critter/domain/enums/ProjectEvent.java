package com.just.critter.domain.enums;

/**
 * 募捐项目状态迁移事件（需求 §8.3）。
 */
public enum ProjectEvent {
    SUBMIT,
    APPROVE,
    REJECT,
    RESUBMIT,
    TARGET_REACHED,
    DEADLINE_REACHED,
    DEADLINE_MISSED,
    SETTLE
}
