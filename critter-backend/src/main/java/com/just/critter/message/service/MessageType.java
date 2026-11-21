package com.just.critter.message.service;

import java.util.Locale;

/**
 * 四类消息与功能分析 FR-N-02 对齐：认养到期、募捐进度、工单进展、审批结果。
 */
public enum MessageType {

    ADOPTION_EXPIRY,
    DONATION_PROGRESS,
    ORDER_PROGRESS,
    APPROVAL_RESULT;

    public static MessageType parse(String value) {
        return MessageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
