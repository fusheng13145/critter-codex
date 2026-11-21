package com.just.critter.service.fund;

import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 项目详情：预算、进度、已入账流水与管理端 allowedActions。
 */
public record ProjectDetail(
        String projectNo,
        String name,
        String purpose,
        ProjectTargetType targetType,
        int targetAmount,
        int raisedAmount,
        int spentAmount,
        int progressPercent,
        LocalDate startDate,
        LocalDate endDate,
        long daysRemaining,
        ProjectStatus status,
        String coverFileId,
        String auditReason,
        Long auditorId,
        long donationCount,
        int budgetTotal,
        List<ProjectBudgetItem> budget,
        List<ProjectDonation> donations,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<String> allowedActions) {
}
