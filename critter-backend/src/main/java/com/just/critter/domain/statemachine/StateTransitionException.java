package com.just.critter.domain.statemachine;

import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;

import java.util.List;

/**
 * 非法状态迁移。返回信息带当前状态与允许的事件，前端可直接展示。
 */
public class StateTransitionException extends BusinessException {

    private final Object current;
    private final Object event;
    private final List<?> allowedEvents;

    public StateTransitionException(ErrorCode errorCode, Object current, Object event, List<?> allowedEvents) {
        super(errorCode.getCode(), describe(current, event, allowedEvents));
        this.current = current;
        this.event = event;
        this.allowedEvents = List.copyOf(allowedEvents);
    }

    private static String describe(Object current, Object event, List<?> allowedEvents) {
        return "当前状态 " + current + " 不允许事件 " + event
                + "，允许的事件：" + (allowedEvents.isEmpty() ? "无（终态）" : allowedEvents);
    }

    public Object getCurrent() {
        return current;
    }

    public Object getEvent() {
        return event;
    }

    public List<?> getAllowedEvents() {
        return allowedEvents;
    }
}
