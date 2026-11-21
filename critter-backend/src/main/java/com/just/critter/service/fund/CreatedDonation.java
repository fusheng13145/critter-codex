package com.just.critter.service.fund;

import com.just.critter.domain.enums.DonationStatus;

/**
 * 登记结果：始终 PENDING_VERIFY，note 向捐赠人说明“登记 ≠ 入账”。
 */
public record CreatedDonation(String donationNo, DonationStatus status, String note) {
}
