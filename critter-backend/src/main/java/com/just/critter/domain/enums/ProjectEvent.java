package com.just.critter.domain.enums;

/**
 * 募捐项目状态迁移事件（需求 §8.3）。
 */
public enum ProjectEvent implements Labeled {
    SUBMIT,
    APPROVE,
    REJECT,
    RESUBMIT,
    TARGET_REACHED,
    DEADLINE_REACHED,
    DEADLINE_MISSED,
    SETTLE;

    @Override
    public String label() {
        return switch (this) {
            case SUBMIT -> "提交审核";
            case APPROVE -> "管理员通过";
            case REJECT -> "管理员驳回";
            case RESUBMIT -> "修改后重新提交";
            case TARGET_REACHED -> "达到目标金额";
            case DEADLINE_REACHED -> "到达截止日";
            case DEADLINE_MISSED -> "到期未达标";
            case SETTLE -> "提交结项报告";
        };
    }
}
