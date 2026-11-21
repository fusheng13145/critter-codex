package com.just.critter.notice.service;

import java.util.Locale;

/**
 * 公告分类。与《功能分析说明书》FR-N-01 的“进展 / 公示 / 科普”一一对应。
 */
public enum NoticeCategory {

    PROGRESS("进展"),
    PUBLICITY("公示"),
    SCIENCE("科普");

    private final String label;

    NoticeCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static NoticeCategory parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("公告分类不能为空");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
