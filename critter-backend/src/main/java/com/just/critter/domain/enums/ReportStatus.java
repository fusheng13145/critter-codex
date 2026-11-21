package com.just.critter.domain.enums;

/**
 * 上报单状态。PENDING 待受理，PROCESSING 已受理转工单，CLOSED 已关闭；
 * 24 小时去重只把 CLOSED 视为已关闭。
 */
public enum ReportStatus {
    PENDING,
    PROCESSING,
    CLOSED
}
