package com.just.critter.controller.fund;

import com.just.critter.service.fund.ExpenseDecision;
import com.just.critter.service.fund.ExpenseReviewCommand;

/**
 * 支出复核请求。复核人是当前登录管理员，secondReviewerId 仅为兼容设计文档保留。
 */
public record ReviewExpenseRequest(ExpenseDecision decision, String reason, String secondReviewerId) {

    public ExpenseReviewCommand toCommand() {
        return new ExpenseReviewCommand(decision, reason);
    }
}
