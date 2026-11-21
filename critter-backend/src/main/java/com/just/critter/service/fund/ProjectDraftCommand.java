package com.just.critter.service.fund;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目创建/修改的写命令。budget 为预算明细；submit=true 时在同一事务内提交审核。
 * 金额单位统一为分。
 */
public record ProjectDraftCommand(
        String name,
        String purpose,
        String targetType,
        Integer targetAmount,
        LocalDate startDate,
        LocalDate endDate,
        List<BudgetItem> budget,
        String coverFileId,
        boolean submit) {

    public record BudgetItem(String subject, Integer amount, String note) {
    }
}
