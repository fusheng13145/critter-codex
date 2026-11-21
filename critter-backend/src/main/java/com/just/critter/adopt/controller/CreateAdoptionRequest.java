package com.just.critter.adopt.controller;

import com.just.critter.adopt.domain.AdoptionApplicationCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /adoptions 请求体。@Valid 只负责快速失败，领域服务仍做最终校验。
 */
public record CreateAdoptionRequest(
        @NotBlank String animalCode,
        @NotNull Long planId,
        @NotNull Boolean promiseAccepted,
        @NotBlank String contact,
        String remark) {

    public AdoptionApplicationCommand toCommand() {
        return new AdoptionApplicationCommand(
                animalCode,
                planId,
                Boolean.TRUE.equals(promiseAccepted),
                contact,
                remark);
    }
}
