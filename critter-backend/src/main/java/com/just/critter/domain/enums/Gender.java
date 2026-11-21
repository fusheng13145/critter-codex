package com.just.critter.domain.enums;

/**
 * 性别。与数据库 ck_animal_gender 一致，未知为默认兜底。
 */
public enum Gender implements Labeled {
    MALE,
    FEMALE,
    UNKNOWN;

    @Override
    public String label() {
        return switch (this) {
            case MALE -> "公";
            case FEMALE -> "母";
            case UNKNOWN -> "未知";
        };
    }
}
