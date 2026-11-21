package com.just.critter.domain.enums;

/**
 * 物种出现数量量级，存枚举而非具体只数（BR-13）。
 */
public enum OccurrenceScale implements Labeled {
    FEW,
    SMALL_HERD,
    LARGE_HERD;

    @Override
    public String label() {
        return switch (this) {
            case FEW -> "零星";
            case SMALL_HERD -> "小群";
            case LARGE_HERD -> "大群";
        };
    }
}
