package com.just.critter.domain.statemachine;

/**
 * 迁移前置条件。不满足时抛出带业务码的 BusinessException，由全局处理器透传。
 */
@FunctionalInterface
public interface Precondition {

    void check(TransitionContext context);
}
