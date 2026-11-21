package com.just.critter.domain.enums;

import java.util.List;

/**
 * 季节筛选用枚举，月份区间固定为：春 3—5、夏 6—8、秋 9—11、冬 12/1/2。
 * 季节不是数据库字段，只在查询时下推月份条件。
 */
public enum Season implements Labeled {

    SPRING("春季", List.of(3, 4, 5)),
    SUMMER("夏季", List.of(6, 7, 8)),
    AUTUMN("秋季", List.of(9, 10, 11)),
    WINTER("冬季", List.of(12, 1, 2));

    private final String label;
    private final List<Integer> months;

    Season(String label, List<Integer> months) {
        this.label = label;
        this.months = months;
    }

    @Override
    public String label() {
        return label;
    }

    public List<Integer> months() {
        return months;
    }
}
