package com.just.critter.domain.enums;

/**
 * 群体档案状态（需求 §8.5），已消失允许回退到活跃。
 */
public enum GroupStatus implements Labeled {
    ACTIVE,
    UNCONFIRMED,
    GONE;

    @Override
    public String label() {
        return switch (this) {
            case ACTIVE -> "活跃";
            case UNCONFIRMED -> "未确认";
            case GONE -> "已消失";
        };
    }
}
