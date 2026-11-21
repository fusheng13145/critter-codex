package com.just.critter.service.fund;

import java.util.List;

/**
 * 捐赠登记入参。金额单位为分，凭证至少一张。
 */
public record CreateDonationCommand(
        String projectNo,
        Integer amount,
        Boolean anonymous,
        String message,
        List<String> voucherFileIds) {
}
