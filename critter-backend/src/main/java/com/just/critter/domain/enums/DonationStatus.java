package com.just.critter.domain.enums;

/**
 * 捐赠状态：待核对、已入账、凭证异常。异常仍需人工核对，不自动回退。
 */
public enum DonationStatus implements Labeled {
    PENDING_VERIFY,
    VERIFIED,
    ABNORMAL;

    @Override
    public String label() {
        return switch (this) {
            case PENDING_VERIFY -> "待核对";
            case VERIFIED -> "已入账";
            case ABNORMAL -> "凭证异常";
        };
    }
}
