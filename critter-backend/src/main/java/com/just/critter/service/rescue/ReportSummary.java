package com.just.critter.service.rescue;

import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 上报列表行：不返回 contact，避免联系方式和公开列表混在一起。
 */
public record ReportSummary(
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
        Long reporterId) {
}
