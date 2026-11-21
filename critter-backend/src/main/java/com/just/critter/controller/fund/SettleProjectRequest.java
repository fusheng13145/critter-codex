package com.just.critter.controller.fund;

import com.just.critter.service.fund.SettleProjectCommand;

import java.util.List;

/**
 * 结项公示请求体；附件为已上传文件编号。
 */
public record SettleProjectRequest(String reportText, String balanceTo, List<String> attachments) {

    public SettleProjectCommand toCommand() {
        return new SettleProjectCommand(reportText, balanceTo, attachments);
    }
}
