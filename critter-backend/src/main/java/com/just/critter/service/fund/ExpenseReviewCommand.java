package com.just.critter.service.fund;

/**
 * 支出复核入参。复核人以当前登录人为准，客户端不能代替他人签字。
 */
public record ExpenseReviewCommand(ExpenseDecision decision, String reason) {
}
