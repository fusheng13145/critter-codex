package com.just.critter.domain.enums;

/**
 * 动物档案状态迁移事件（需求 §8.1）。
 */
public enum AnimalEvent implements Labeled {
    REPORT_ACCEPTED,
    SEND_TO_HOSPITAL,
    SURGERY_DONE,
    RELEASE,
    INJURY_FOUND,
    ADOPTION_COMPLETED,
    NO_SIGHTING_30D,
    CONFIRM_DEATH;

    @Override
    public String label() {
        return switch (this) {
            case REPORT_ACCEPTED -> "上报受伤并被受理";
            case SEND_TO_HOSPITAL -> "送医并登记就诊记录";
            case SURGERY_DONE -> "手术完成且恢复稳定";
            case RELEASE -> "按计划放归";
            case INJURY_FOUND -> "再次被发现伤病";
            case ADOPTION_COMPLETED -> "领养办结并交接";
            case NO_SIGHTING_30D -> "连续三十日无目击";
            case CONFIRM_DEATH -> "确认死亡";
        };
    }
}
