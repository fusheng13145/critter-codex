package com.just.critter.service.fund;

import java.util.List;

/**
 * 凭证异常标记命令：只允许未入账或已异常的单据。
 */
public record FlagDonationCommand(String reason, List<String> voucherFileIds) {
}
