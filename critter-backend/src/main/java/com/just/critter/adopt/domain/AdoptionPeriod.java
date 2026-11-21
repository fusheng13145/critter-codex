package com.just.critter.adopt.domain;

/**
 * 云认养周期。当前契约只允许按月、按季，不包含原型中的年度方案。
 */
public enum AdoptionPeriod {
    MONTH(1),
    QUARTER(3);

    private final int months;

    AdoptionPeriod(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
