package com.just.critter.domain.statemachine;

import com.just.critter.domain.enums.Labeled;

/**
 * 状态/事件的展示文本：message 用中文名，data 用枚举码。
 */
final class StateText {

    private StateText() {
    }

    static String code(Object value) {
        return value == null ? null : value.toString();
    }

    static String label(Object value) {
        if (value == null) {
            return "未知";
        }
        return value instanceof Labeled labeled ? labeled.label() : value.toString();
    }
}
