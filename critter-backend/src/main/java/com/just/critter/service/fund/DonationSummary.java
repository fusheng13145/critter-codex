package com.just.critter.service.fund;

import com.just.critter.domain.enums.DonationStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理员捐赠列表行；不暴露联系方式，仅保留展示名与凭证。
 */
public record DonationSummary(
        String donationNo,
        String projectNo,
        String projectName,
        Long userId,
        String donorDisplay,
        Integer amount,
        Boolean anonymous,
        String message,
        List<String> voucherFileIds,
        DonationStatus status,
        Long verifierId,
        OffsetDateTime verifiedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
