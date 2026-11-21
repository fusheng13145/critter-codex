package com.just.critter.service.archive;

import com.just.critter.domain.enums.HealthType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 健康记录视图。reminderDue 仅对带计划日期的疫苗记录计算：
 * 计划日期在三天内（含已逾期）为 true，前端据此显示待办角标。
 */
public record HealthRecordView(
        Long id,
        HealthType type,
        String typeLabel,
        OffsetDateTime recordedAt,
        String result,
        LocalDate nextDate,
        boolean reminderDue,
        Integer daysUntilNext,
        Long operatorId,
        OffsetDateTime createdAt) {
}
