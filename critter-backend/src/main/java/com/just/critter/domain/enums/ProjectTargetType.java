package com.just.critter.domain.enums;

/**
 * 募捐项目关联对象类型，与 project.target_type 的 CHECK 约束一致。
 */
public enum ProjectTargetType implements Labeled {
    ANIMAL,
    GROUP,
    MIXED;

    @Override
    public String label() {
        return switch (this) {
            case ANIMAL -> "个体";
            case GROUP -> "群体";
            case MIXED -> "混合";
        };
    }
}
