package com.just.critter.service.fund;

/**
 * 台账合计。只计已入账捐赠与已复核支出，balance 恒等于 income - expense。
 */
public record LedgerSummary(long income, long expense, long balance, int donationCount, int expenseCount) {
}
