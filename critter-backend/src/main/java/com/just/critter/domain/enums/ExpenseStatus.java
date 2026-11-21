package com.just.critter.domain.enums;

/**
 * 支出复核状态。超 500 元需两名复核人，见 ck_expense_double_sign。
 */
public enum ExpenseStatus implements Labeled {
    PENDING_REVIEW,
    FIRST_APPROVED,
    APPROVED,
    REJECTED;

    @Override
    public String label() {
        return switch (this) {
            case PENDING_REVIEW -> "待复核";
            case FIRST_APPROVED -> "初审通过";
            case APPROVED -> "已复核";
            case REJECTED -> "已驳回";
        };
    }
}
