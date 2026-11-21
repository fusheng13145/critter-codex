package com.just.critter.controller.fund;

import com.just.critter.service.fund.ConfirmDonationCommand;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 核对入账请求体。实收金额缺省时按登记金额核对；paidAt 仅进审计日志。
 */
public record ConfirmDonationRequest(
        Integer amount,
        OffsetDateTime paidAt,
        List<String> voucherFileIds,
        String note) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public ConfirmDonationCommand toCommand() {
        LocalDateTime localPaidAt = paidAt == null ? null
                : paidAt.atZoneSameInstant(ZONE).toLocalDateTime();
        return new ConfirmDonationCommand(amount, localPaidAt, voucherFileIds, note);
    }
}
