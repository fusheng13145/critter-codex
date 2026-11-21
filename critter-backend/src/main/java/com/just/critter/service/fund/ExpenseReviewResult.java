package com.just.critter.service.fund;

import com.just.critter.domain.enums.ExpenseStatus;

/**
 * 复核结果。needSecondReview=true 表示大额支出只完成第一签。
 */
public record ExpenseReviewResult(
        String expenseNo,
        ExpenseStatus status,
        boolean needSecondReview,
        ProjectFundSnapshot project,
        String message) {
}
