package com.just.critter.domain.statemachine;

import com.just.critter.domain.enums.AnimalEvent;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.enums.Role;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 验收标准（概要设计 §3 决策一）：状态迁移表驱动，业务只发事件；
 * 非法迁移统一抛 StateTransitionException，返回当前状态与允许集合；
 * 权限与前置条件在迁移表里声明，不在业务代码里堆 if。
 */
class StateMachineTest {

    enum Sample { A, B, C }

    enum Event { GO, BACK, JUMP }

    private StateMachine<Sample, Event> machine() {
        return new StateMachine<>(
                ErrorCode.STATE_TRANSITION_INVALID,
                List.of(
                        new Transition<>(Sample.A, Event.GO, Sample.B, Set.of(), null),
                        new Transition<>(Sample.B, Event.BACK, Sample.A, Set.of(Role.ANIMAL_ADMIN), null)
                ));
    }

    @Test
    @DisplayName("合法迁移返回目标状态")
    void legal_transition_returns_target() {
        assertThat(machine().fire(Sample.A, Event.GO, TransitionContext.of(Role.STUDENT))).isEqualTo(Sample.B);
    }

    @Test
    @DisplayName("非法迁移抛异常，错误码 30402，并给出允许的事件")
    void illegal_transition_reports_allowed_events() {
        assertThatThrownBy(() -> machine().fire(Sample.A, Event.BACK, TransitionContext.of(Role.STUDENT)))
                .isInstanceOf(StateTransitionException.class)
                .satisfies(ex -> {
                    StateTransitionException transition = (StateTransitionException) ex;
                    assertThat(transition.getCode()).isEqualTo(30402);
                    assertThat(transition.getCurrent()).isEqualTo(Sample.A);
                    assertThat(transition.getAllowedEvents()).isEqualTo(List.of(Event.GO));
                    assertThat(transition.getMessage()).contains("A");
                });
    }

    @Test
    @DisplayName("非法迁移：message 用中文状态与事件名，data 带允许的目标状态")
    void illegal_transition_returns_chinese_message_and_targets() {
        StateTransitionException ex = catchThrowableOfType(
                () -> StateMachines.animal().fire(AnimalStatus.ON_CAMPUS, AnimalEvent.SEND_TO_HOSPITAL,
                        TransitionContext.of(Role.ANIMAL_ADMIN)),
                StateTransitionException.class);

        Map<?, ?> data = (Map<?, ?>) ex.getData();
        assertThat(data.get("current")).isEqualTo("ON_CAMPUS");
        assertThat(data.get("target")).isNull();
        assertThat(data.get("allowed")).isEqualTo(
                List.of("TREATING", "ADOPTED", "MISSING", "DECEASED"));
        assertThat(ex.getMessage())
                .contains("在校园活动", "送医并登记就诊记录", "治疗中")
                .doesNotContain("ON_CAMPUS", "SEND_TO_HOSPITAL");
    }

    @Test
    @DisplayName("非法迁移：终态没有可选目标，data.allowed 为空")
    void terminal_state_has_empty_allowed() {
        StateTransitionException ex = catchThrowableOfType(
                () -> StateMachines.animal().fire(AnimalStatus.DECEASED, AnimalEvent.CONFIRM_DEATH,
                        TransitionContext.of(Role.ANIMAL_ADMIN)),
                StateTransitionException.class);

        assertThat((List<?>) ((Map<?, ?>) ex.getData()).get("allowed")).isEmpty();
        assertThat(ex.getMessage()).contains("已离世", "确认死亡", "无（终态）");
    }

    @Test
    @DisplayName("角色不符：data 记录被拒绝的目标状态")
    void role_denied_reports_target() {
        StateTransitionException ex = catchThrowableOfType(
                () -> StateMachines.animal().fire(AnimalStatus.ON_CAMPUS, AnimalEvent.REPORT_ACCEPTED,
                        TransitionContext.of(Role.STUDENT)),
                StateTransitionException.class);

        assertThat(((Map<?, ?>) ex.getData()).get("target")).isEqualTo("TREATING");
        assertThat(ex.getMessage()).contains("治疗中");
    }

    @Test
    @DisplayName("迁移受角色限制，角色不符时拒绝")
    void role_is_enforced() {
        assertThatThrownBy(() -> machine().fire(Sample.B, Event.BACK, TransitionContext.of(Role.STUDENT)))
                .isInstanceOf(StateTransitionException.class);

        assertThat(machine().fire(Sample.B, Event.BACK, TransitionContext.of(Role.ANIMAL_ADMIN)))
                .isEqualTo(Sample.A);
    }

    @Test
    @DisplayName("前置条件不满足时抛出带业务码的异常，不吞掉原因")
    void precondition_failure_keeps_business_code() {
        StateMachine<Sample, Event> guarded = new StateMachine<>(
                ErrorCode.STATE_TRANSITION_INVALID,
                List.of(new Transition<>(Sample.A, Event.GO, Sample.B, Set.of(),
                        ctx -> {
                            throw new BusinessException(ErrorCode.TNR_RECORD_MISSING);
                        })));

        assertThatThrownBy(() -> guarded.fire(Sample.A, Event.GO, TransitionContext.of(Role.ANIMAL_ADMIN)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30603));
    }

    @Test
    @DisplayName("同一状态同一事件只允许一条迁移")
    void duplicate_transition_is_rejected() {
        assertThatThrownBy(() -> new StateMachine<>(ErrorCode.STATE_TRANSITION_INVALID, List.of(
                new Transition<>(Sample.A, Event.GO, Sample.B, Set.of(), null),
                new Transition<>(Sample.A, Event.GO, Sample.C, Set.of(), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GO");
    }

    @Test
    @DisplayName("没有任何迁移的状态视为终态")
    void state_without_transition_is_terminal() {
        assertThat(machine().isTerminal(Sample.C)).isTrue();
        assertThat(machine().isTerminal(Sample.A)).isFalse();
    }
}
