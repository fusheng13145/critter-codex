package com.just.critter.controller.fund;

import com.just.critter.service.fund.ProjectAuditCommand;

/**
 * 项目审核请求体。decision 支持 APPROVE / REJECT；驳回时 reason 必填。
 */
public record ProjectAuditRequest(String decision, String reason) {

    public ProjectAuditCommand toCommand() {
        return new ProjectAuditCommand(decision, reason);
    }
}
