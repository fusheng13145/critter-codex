package com.just.critter.controller.fund;

import com.just.critter.service.fund.CreateDonationCommand;

import java.util.List;

/**
 * 捐赠登记请求体。amount 单位为分；anonymous 必填，message 与凭证按文档校验。
 */
public record CreateDonationRequest(
        String projectNo,
        Integer amount,
        Boolean anonymous,
        String message,
        List<String> voucherFileIds) {

    public CreateDonationCommand toCommand() {
        return new CreateDonationCommand(projectNo, amount, anonymous, message, voucherFileIds);
    }
}
