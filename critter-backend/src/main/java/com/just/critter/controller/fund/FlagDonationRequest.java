package com.just.critter.controller.fund;

import com.just.critter.service.fund.FlagDonationCommand;

import java.util.List;

/**
 * 凭证异常标记请求。
 */
public record FlagDonationRequest(String reason, List<String> voucherFileIds) {

    public FlagDonationCommand toCommand() {
        return new FlagDonationCommand(reason, voucherFileIds);
    }
}
