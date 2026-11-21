package com.just.critter.service.fund;

import com.just.critter.domain.enums.DonationStatus;

import java.time.OffsetDateTime;

/**
 * 核对结果：status 为 VERIFIED 时带 verifiedAt 与最新项目进度；
 * ABNORMAL 时 verifiedAt 为 null，进度保持原值。
 */
public record DonationConfirmation(
        String donationNo,
        DonationStatus status,
        Integer amount,
        OffsetDateTime verifiedAt,
        ProjectProgress progress,
        String note) {
}
