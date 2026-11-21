package com.just.critter.service.fund;

import java.time.OffsetDateTime;

/**
 * 公开项目页的已入账捐赠流水（脱敏：只含展示名，不含用户 ID / 联系方式）。
 */
public record ProjectDonation(
        String donationNo,
        String donorDisplay,
        int amount,
        String message,
        OffsetDateTime verifiedAt) {
}
