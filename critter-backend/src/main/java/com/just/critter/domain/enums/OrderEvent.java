package com.just.critter.domain.enums;

/**
 * 救助工单状态迁移事件（需求 §8.4）。
 */
public enum OrderEvent implements Labeled {
    ACCEPT,
    DISPOSE_DONE,
    CANNOT_DISPOSE,
    REVISIT_OK,
    REVISIT_ANOMALY,
    TIMEOUT;

    @Override
    public String label() {
        return switch (this) {
            case ACCEPT -> "受理并指派处置人";
            case DISPOSE_DONE -> "完成送医、手术或放归等处置";
            case CANNOT_DISPOSE -> "无法处置或动物死亡";
            case REVISIT_OK -> "回访确认恢复良好";
            case REVISIT_ANOMALY -> "回访发现异常";
            case TIMEOUT -> "超过二十四小时未受理";
        };
    }
}
