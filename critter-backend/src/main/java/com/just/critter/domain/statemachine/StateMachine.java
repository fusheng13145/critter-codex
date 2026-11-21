package com.just.critter.domain.statemachine;

import com.just.critter.support.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 表驱动状态机：业务只发事件，能不能迁由表决定。新增状态只改表，不改代码。
 */
public class StateMachine<S, E> {

    private final ErrorCode illegalCode;
    private final Map<S, List<Transition<S, E>>> byFrom;
    private final Set<S> states;

    public StateMachine(ErrorCode illegalCode, List<Transition<S, E>> transitions) {
        this(illegalCode, transitions, null);
    }

    public StateMachine(ErrorCode illegalCode, List<Transition<S, E>> transitions, Set<S> knownStates) {
        this.illegalCode = Objects.requireNonNull(illegalCode);
        Map<S, List<Transition<S, E>>> grouped = new LinkedHashMap<>();
        Set<String> keys = new HashSet<>();
        for (Transition<S, E> transition : transitions) {
            String key = transition.from() + "::" + transition.event();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("同一状态存在重复事件定义：" + transition.event());
            }
            grouped.computeIfAbsent(transition.from(), state -> new ArrayList<>()).add(transition);
        }
        grouped.replaceAll((state, items) -> List.copyOf(items));
        this.byFrom = Collections.unmodifiableMap(grouped);

        if (knownStates != null) {
            this.states = Collections.unmodifiableSet(new LinkedHashSet<>(knownStates));
        } else {
            Set<S> derived = new LinkedHashSet<>();
            transitions.forEach(transition -> {
                derived.add(transition.from());
                derived.add(transition.to());
            });
            this.states = Collections.unmodifiableSet(derived);
        }
    }

    public S fire(S current, E event, TransitionContext context) {
        Objects.requireNonNull(context, "迁移上下文不能为空");
        Transition<S, E> transition = byFrom.getOrDefault(current, List.of()).stream()
                .filter(item -> item.event().equals(event))
                .findFirst()
                .orElseThrow(() -> new StateTransitionException(
                        illegalCode, current, null, event, allowedTargets(current), allowedEvents(current)));

        if (!transition.allows(context.getRole())) {
            throw new StateTransitionException(illegalCode, current, transition.to(), event,
                    allowedTargets(current), allowedEvents(current));
        }
        if (transition.hasPrecondition()) {
            transition.precondition().check(context);
        }
        return transition.to();
    }

    public List<E> allowedEvents(S current) {
        return byFrom.getOrDefault(current, List.of()).stream().map(Transition::event).toList();
    }

    /** 允许的目标状态：前端据此禁用状态按钮（接口设计说明书 §7.4）。 */
    public List<S> allowedTargets(S current) {
        return byFrom.getOrDefault(current, List.of()).stream().map(Transition::to).toList();
    }

    public boolean isTerminal(S state) {
        return byFrom.getOrDefault(state, List.of()).isEmpty();
    }

    public List<S> statusesWithoutEscape() {
        return states.stream().filter(this::isTerminal).toList();
    }

    public List<Transition<S, E>> transitions() {
        return byFrom.values().stream().flatMap(List::stream).toList();
    }

    public Set<S> states() {
        return states;
    }

    public ErrorCode getIllegalCode() {
        return illegalCode;
    }
}
