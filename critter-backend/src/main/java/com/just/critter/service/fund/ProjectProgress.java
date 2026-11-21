package com.just.critter.service.fund;

/**
 * 核对后的项目进度快照；only VERIFIED donations are included in raisedAmount.
 */
public record ProjectProgress(String projectNo, Integer raisedAmount,
                              Integer targetAmount, Integer progressPercent) {
}
