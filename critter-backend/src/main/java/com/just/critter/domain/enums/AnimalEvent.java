package com.just.critter.domain.enums;

/**
 * 动物档案状态迁移事件（需求 §8.1）。
 */
public enum AnimalEvent {
    REPORT_ACCEPTED,
    SEND_TO_HOSPITAL,
    SURGERY_DONE,
    RELEASE,
    INJURY_FOUND,
    ADOPTION_COMPLETED,
    NO_SIGHTING_30D,
    CONFIRM_DEATH
}
