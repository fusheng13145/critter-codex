package com.just.critter.service.fund;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单笔核对入参。amount 缺省时按登记金额核对；paidAt 只进审计日志，不覆盖 verifiedAt。
 */
public record ConfirmDonationCommand(
        Integer amount,
        LocalDateTime paidAt,
        List<String> voucherFileIds,
        String note) {
}
