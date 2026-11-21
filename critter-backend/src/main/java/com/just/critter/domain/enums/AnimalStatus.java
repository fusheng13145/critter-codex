package com.just.critter.domain.enums;

/**
 * 动物档案状态（需求 §8.1），已离世为终态。
 */
public enum AnimalStatus implements Labeled {
    ON_CAMPUS,
    TREATING,
    HOSPITALIZED,
    TO_RELEASE,
    RELEASED,
    ADOPTED,
    MISSING,
    DECEASED;

    @Override
    public String label() {
        return switch (this) {
            case ON_CAMPUS -> "在校园活动";
            case TREATING -> "治疗中";
            case HOSPITALIZED -> "住院观察";
            case TO_RELEASE -> "待放归";
            case RELEASED -> "已放归";
            case ADOPTED -> "已领养";
            case MISSING -> "失踪";
            case DECEASED -> "已离世";
        };
    }
}
