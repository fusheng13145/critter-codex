package com.just.critter.service.fund;

/**
 * 项目审核命令。decision 支持 APPROVE / REJECT（大小写不敏感）；
 * 驳回时 reason 必填，通过时可为空。
 */
public record ProjectAuditCommand(String decision, String reason) {
}
