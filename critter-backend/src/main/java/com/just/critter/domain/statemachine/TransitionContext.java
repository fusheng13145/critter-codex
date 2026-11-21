package com.just.critter.domain.statemachine;

import com.just.critter.domain.enums.Role;

import java.util.Map;
import java.util.Objects;

/**
 * 迁移上下文：谁在操作，以及本次迁移依赖的业务事实（如是否已有手术记录）。
 */
public final class TransitionContext {

    private final Role role;
    private final Map<String, Object> attributes;

    private TransitionContext(Role role, Map<String, Object> attributes) {
        this.role = role;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static TransitionContext of(Role role) {
        return new TransitionContext(role, Map.of());
    }

    public static TransitionContext of(Role role, String key, Object value) {
        return new TransitionContext(role, Map.of(key, value));
    }

    public Role getRole() {
        return role;
    }

    public boolean has(String key) {
        return attributes.containsKey(key);
    }

    public boolean flag(String key) {
        Object value = attributes.get(key);
        return value instanceof Boolean bool && bool;
    }

    public String text(String key) {
        Object value = attributes.get(key);
        return Objects.toString(value, null);
    }

    public Object get(String key) {
        return attributes.get(key);
    }
}
