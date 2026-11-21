package com.just.critter.service.fund;

import com.just.critter.domain.enums.DonationStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 凭证异常标记结果。
 */
public record DonationFlagResult(
        String donationNo,
        DonationStatus status,
        String abnormalReason,
        List<String> voucherFileIds,
        OffsetDateTime flaggedAt) {
}
