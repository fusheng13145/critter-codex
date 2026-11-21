package com.just.critter.service.fund;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 支出登记入参。金额单位为分，票据至少一张，支出必须同时挂项目与动物。
 */
public record CreateExpenseCommand(
        String projectNo,
        String animalCode,
        String purpose,
        Integer amount,
        String payee,
        OffsetDateTime occurredAt,
        List<String> voucherFileIds) {
}
