package com.just.critter.adopt.domain;

/**
 * 提交认养申请的输入。必填项在这里保留可空类型，交由应用服务统一校验，
 * 避免 Controller 校验与领域校验出现两套口径。
 */
public record AdoptionApplicationCommand(
        String animalCode,
        Long planId,
        boolean promiseAccepted,
        String contact,
        String remark) {
}
