package com.just.critter.domain.enums;

/**
 * 群体档案状态（需求 §8.5），已消失允许回退到活跃。
 */
public enum GroupStatus {
    ACTIVE,
    UNCONFIRMED,
    GONE
}
