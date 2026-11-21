package com.just.critter.domain.enums;

/**
 * 救助工单状态（需求 §8.4）。
 */
public enum OrderStatus implements Labeled {
    PENDING,
    PROCESSING,
    TO_REVISIT,
    TIMEOUT,
    CLOSED;

    @Override
    public String label() {
        return switch (this) {
            case PENDING -> "待受理";
            case PROCESSING -> "处理中";
            case TO_REVISIT -> "待回访";
            case TIMEOUT -> "已超时";
            case CLOSED -> "已关闭";
        };
    }
}
