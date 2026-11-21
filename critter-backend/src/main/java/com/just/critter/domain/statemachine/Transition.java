package com.just.critter.domain.statemachine;

import com.just.critter.domain.enums.Role;

import java.util.Set;

/**
 * 一条状态迁移：从哪来、发什么事件、到哪去、谁能做、需要满足什么。
 */
public record Transition<S, E>(S from, E event, S to, Set<Role> allowedRoles, Precondition precondition) {

    public boolean allows(Role role) {
        return allowedRoles == null || allowedRoles.isEmpty() || allowedRoles.contains(role);
    }

    public boolean hasPrecondition() {
        return precondition != null;
    }
}
