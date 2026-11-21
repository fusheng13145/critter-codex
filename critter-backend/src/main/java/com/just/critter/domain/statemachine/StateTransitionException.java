package com.just.critter.domain.statemachine;

import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 非法状态迁移。接口口径（接口设计说明书 §7.4）：
 * message 用中文状态与事件名可直接展示；data 固定为 {current, target, allowed}，
 * 其中 allowed 是允许的目标状态，前端据此禁用按钮。
 */
public class StateTransitionException extends BusinessException {

    private final Object current;
    private final Object target;
    private final Object event;
    private final List<?> allowedTargets;
    private final List<?> allowedEvents;

    public StateTransitionException(ErrorCode errorCode, Object current, Object target, Object event,
                                    List<?> allowedTargets, List<?> allowedEvents) {
        super(errorCode.getCode(), describe(current, target, event, allowedTargets),
                buildData(current, target, allowedTargets));
        this.current = current;
        this.target = target;
        this.event = event;
        this.allowedTargets = List.copyOf(allowedTargets);
        this.allowedEvents = List.copyOf(allowedEvents);
    }

    private static String describe(Object current, Object target, Object event, List<?> allowedTargets) {
        StringBuilder text = new StringBuilder()
                .append("当前状态『").append(StateText.label(current))
                .append("』不允许执行事件『").append(StateText.label(event)).append("』");
        if (target != null) {
            text.append("变更为『").append(StateText.label(target)).append("』");
        }
        String allowed = allowedTargets.isEmpty()
                ? "无（终态）"
                : allowedTargets.stream().map(StateText::label).collect(Collectors.joining("、"));
        return text.append("；允许的目标状态：").append(allowed).toString();
    }

    private static Map<String, Object> buildData(Object current, Object target, List<?> allowedTargets) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("current", StateText.code(current));
        data.put("target", StateText.code(target));
        data.put("allowed", allowedTargets.stream().map(StateText::code).toList());
        return data;
    }

    public Object getCurrent() {
        return current;
    }

    public Object getTarget() {
        return target;
    }

    public Object getEvent() {
        return event;
    }

    public List<?> getAllowedTargets() {
        return allowedTargets;
    }

    public List<?> getAllowedEvents() {
        return allowedEvents;
    }
}
