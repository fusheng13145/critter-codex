package com.just.critter.service.fund;

import com.just.critter.domain.enums.ProjectStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 审核结果：新状态、审核人、理由与后续允许的管理动作。
 */
public record ProjectAuditResult(
        String projectNo,
        ProjectStatus status,
        String auditReason,
        Long auditorId,
        OffsetDateTime auditedAt,
        List<String> allowedActions) {
}
