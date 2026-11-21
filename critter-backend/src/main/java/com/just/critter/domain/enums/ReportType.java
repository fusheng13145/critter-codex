package com.just.critter.domain.enums;

/**
 * 上报类型（03 §10）。学生上报必选其一。
 */
public enum ReportType implements Labeled {
    INJURED,
    NEW_ARRIVAL,
    ABANDONED,
    DISTURB,
    DEAD;

    @Override
    public String label() {
        return switch (this) {
            case INJURED -> "受伤";
            case NEW_ARRIVAL -> "新出现";
            case ABANDONED -> "被遗弃";
            case DISTURB -> "扰民";
            case DEAD -> "死亡";
        };
    }
}
