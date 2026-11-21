package com.just.critter.adopt.controller;

import com.just.critter.adopt.domain.AdoptionAuditCommand;
import com.just.critter.adopt.domain.AdoptionDecision;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /adoptions/{no}/audit 请求体。reason 与 amount 的联动规则由领域服务最终校验。
 */
public record AdoptionAuditRequest(
        @NotNull AdoptionDecision decision,
        String reason,
        Integer amount) {

    public AdoptionAuditCommand toCommand() {
        return new AdoptionAuditCommand(decision, reason, amount);
    }
}
