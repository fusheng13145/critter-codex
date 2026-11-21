package com.just.critter.domain.enums;

/**
 * 健康与防疫记录类型，与 ck_health_type 一致。
 */
public enum HealthType implements Labeled {
    VACCINE,
    DEWORM,
    WEIGHT,
    HISTORY;

    @Override
    public String label() {
        return switch (this) {
            case VACCINE -> "疫苗";
            case DEWORM -> "驱虫";
            case WEIGHT -> "体重";
            case HISTORY -> "病史";
        };
    }
}
