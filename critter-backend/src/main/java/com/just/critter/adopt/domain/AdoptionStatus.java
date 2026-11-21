package com.just.critter.adopt.domain;

/**
 * 认养单状态，与数据库 ck_adoption_status 保持一致。
 */
public enum AdoptionStatus {
    PENDING_AUDIT,
    PENDING_PAYMENT,
    ACTIVE,
    EXPIRED,
    CLOSED,
    REJECTED,
    TERMINATED
}
