package com.just.critter.service.fund;

import com.just.critter.domain.enums.ExpenseStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 支出列表视图。金额为整数分；handlerName 只用于展示，权限仍按角色控制。
 */
public record ExpenseSummary(
        String expenseNo,
        String projectNo,
        String projectName,
        String animalCode,
        String animalName,
        String purpose,
        int amount,
        String payee,
        OffsetDateTime occurredAt,
        Long handlerId,
        String handlerName,
        List<String> voucherFileIds,
        ExpenseStatus status,
        Long firstReviewerId,
        Long secondReviewerId,
        String reviewReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
