package com.just.critter.service.rescue;

import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 上报详情。contact 只对管理员或上报人本人返回，其余登录用户为 null。
 */
public record ReportDetail(
        Long id,
        String reportNo,
        ReportType type,
        Long pointId,
        String pointName,
        String description,
        List<String> photos,
        ReportStatus status,
        boolean overdue,
        LocalDateTime createdAt,
        OffsetDateTime deadline,
        Long reporterId,
        Long animalId,
        String animalCode,
        String contact,
        String orderNo,
        OffsetDateTime confirmedAt,
        OffsetDateTime confirmDueAt,
        boolean autoConfirmed,
        boolean invalid,
        String invalidReason) {
}
