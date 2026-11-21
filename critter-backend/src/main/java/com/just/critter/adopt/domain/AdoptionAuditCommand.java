package com.just.critter.adopt.domain;

/**
 * 审核命令。amount 为空时沿用申请书快照金额；调整金额必须同时给出说明。
 */
public record AdoptionAuditCommand(
        AdoptionDecision decision,
        String reason,
        Integer amount) {
}
