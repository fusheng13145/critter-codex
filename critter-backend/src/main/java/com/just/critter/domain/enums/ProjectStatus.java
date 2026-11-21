package com.just.critter.domain.enums;

/**
 * 募捐项目状态（需求 §8.3），已结项与已结束为终态。
 */
public enum ProjectStatus implements Labeled {
    DRAFT,
    PENDING_AUDIT,
    FUNDING,
    PENDING_SETTLE,
    SETTLED,
    ENDED,
    REJECTED;

    @Override
    public String label() {
        return switch (this) {
            case DRAFT -> "草稿";
            case PENDING_AUDIT -> "待审核";
            case FUNDING -> "募捐中";
            case PENDING_SETTLE -> "待结项";
            case SETTLED -> "已结项";
            case ENDED -> "已结束";
            case REJECTED -> "已驳回";
        };
    }
}
