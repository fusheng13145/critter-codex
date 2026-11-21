package com.just.critter.service.fund;

import com.just.critter.domain.enums.ExpenseStatus;

/**
 * 支出登记回执。登记不等于入账，note 明确提示待复核。
 */
public record CreatedExpense(String expenseNo, ExpenseStatus status, String note) {
}
