package com.just.critter.job.fund;

/**
 * 一次项目到期扫描结果。
 *
 * @param candidates 到期仍处于 FUNDING 的项目数
 * @param pendingSettle 本次转入 PENDING_SETTLE 的数量（达到目标）
 * @param ended 本次转入 ENDED 的数量（未达到目标）
 */
public record ProjectDeadlineScanResult(int candidates, int pendingSettle, int ended) {
}
