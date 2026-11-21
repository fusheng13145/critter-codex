package com.just.critter.service.fund;

import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 项目列表行：公开页需要的目标、已筹、已支出、进度、剩余天数与捐赠笔数。
 */
public record ProjectSummary(
        String projectNo,
        String name,
        ProjectTargetType targetType,
        int targetAmount,
        int raisedAmount,
        int spentAmount,
        int progressPercent,
        LocalDate startDate,
        LocalDate endDate,
        long daysRemaining,
        ProjectStatus status,
        long donationCount,
        String coverFileId,
        OffsetDateTime createdAt) {
}
