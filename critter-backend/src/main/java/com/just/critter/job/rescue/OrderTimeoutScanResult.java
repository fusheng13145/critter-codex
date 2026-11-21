package com.just.critter.job.rescue;

/**
 * 一次超时扫描结果。candidates 为到期待受理工单数，timedOut 为本次成功置为 TIMEOUT 的数量。
 */
public record OrderTimeoutScanResult(int candidates, int timedOut) {
}
