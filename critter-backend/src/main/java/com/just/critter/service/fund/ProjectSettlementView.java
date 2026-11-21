package com.just.critter.service.fund;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 结项公示只读视图。余额 = totalRaised - totalSpent。
 */
public record ProjectSettlementView(
        String projectNo,
        String projectName,
        String reportText,
        int totalRaised,
        int totalSpent,
        int balance,
        String balanceTo,
        List<String> attachments,
        OffsetDateTime publishedAt) {
}
