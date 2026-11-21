package com.just.critter.controller.fund;

import com.just.critter.service.fund.CreateExpenseCommand;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 支出登记请求。金额单位为分，occurredAt 用带时区的 ISO-8601。
 */
public record CreateExpenseRequest(
        String projectNo,
        String animalCode,
        String purpose,
        Integer amount,
        String payee,
        OffsetDateTime occurredAt,
        List<String> voucherFileIds) {

    public CreateExpenseCommand toCommand() {
        return new CreateExpenseCommand(projectNo, animalCode, purpose, amount, payee,
                occurredAt, voucherFileIds);
    }
}
