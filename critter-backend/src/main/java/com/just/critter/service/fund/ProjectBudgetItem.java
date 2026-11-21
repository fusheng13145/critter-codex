package com.just.critter.service.fund;

/**
 * 预算明细的只读展示模型。
 */
public record ProjectBudgetItem(String subject, int amount, String note) {
}
