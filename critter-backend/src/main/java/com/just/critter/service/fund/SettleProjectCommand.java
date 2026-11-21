package com.just.critter.service.fund;

import java.util.List;

/**
 * 结项公示提交入参。附件为票据/报告 fileId，可为空。
 */
public record SettleProjectCommand(String reportText, String balanceTo, List<String> attachments) {
}
