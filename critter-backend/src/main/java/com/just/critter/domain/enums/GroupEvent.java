package com.just.critter.domain.enums;

/**
 * 群体档案状态迁移事件（需求 §8.5）。
 */
public enum GroupEvent implements Labeled {
    NO_SIGHTING_60D,
    SIGHTING_CONFIRMED,
    CONFIRM_GONE;

    @Override
    public String label() {
        return switch (this) {
            case NO_SIGHTING_60D -> "连续六十日无目击";
            case SIGHTING_CONFIRMED -> "再次目击并确认归属";
            case CONFIRM_GONE -> "确认迁离或消失";
        };
    }
}
