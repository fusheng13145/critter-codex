package com.just.critter.service.rescue;

import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;

import java.util.List;

/**
 * 上报列表筛选。overdue=true 只看超过 24 小时未关闭；false 只看 24 小时内未关闭。
 */
public record ReportFilter(
        List<ReportStatus> statuses,
        ReportType type,
        Long pointId,
        Boolean overdue) {
}
