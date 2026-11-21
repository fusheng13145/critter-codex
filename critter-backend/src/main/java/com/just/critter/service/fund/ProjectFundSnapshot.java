package com.just.critter.service.fund;

/**
 * 复核后的项目资金快照。balance 恒等于 raised - spent。
 */
public record ProjectFundSnapshot(String projectNo, int raised, int spent, int balance) {
}
