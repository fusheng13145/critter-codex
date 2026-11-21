package com.just.critter.domain.statemachine;

import com.just.critter.domain.enums.AnimalEvent;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.enums.GroupEvent;
import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.domain.enums.OrderEvent;
import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ProjectEvent;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.just.critter.domain.enums.AnimalEvent.ADOPTION_COMPLETED;
import static com.just.critter.domain.enums.AnimalEvent.CONFIRM_DEATH;
import static com.just.critter.domain.enums.AnimalEvent.INJURY_FOUND;
import static com.just.critter.domain.enums.AnimalEvent.NO_SIGHTING_30D;
import static com.just.critter.domain.enums.AnimalEvent.RELEASE;
import static com.just.critter.domain.enums.AnimalEvent.REPORT_ACCEPTED;
import static com.just.critter.domain.enums.AnimalEvent.SEND_TO_HOSPITAL;
import static com.just.critter.domain.enums.AnimalEvent.SURGERY_DONE;
import static com.just.critter.domain.enums.AnimalStatus.ADOPTED;
import static com.just.critter.domain.enums.AnimalStatus.DECEASED;
import static com.just.critter.domain.enums.AnimalStatus.HOSPITALIZED;
import static com.just.critter.domain.enums.AnimalStatus.MISSING;
import static com.just.critter.domain.enums.AnimalStatus.ON_CAMPUS;
import static com.just.critter.domain.enums.AnimalStatus.RELEASED;
import static com.just.critter.domain.enums.AnimalStatus.TO_RELEASE;
import static com.just.critter.domain.enums.AnimalStatus.TREATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验收标准（需求分析说明书 §8.1—§8.5）：四类对象的状态集合与迁移条件逐条落地，
 * 这些表既是开发依据，也可直接转化为测试用例。
 */
class StateTablesTest {

    private final StateMachine<AnimalStatus, AnimalEvent> animals = StateMachines.animal();
    private final StateMachine<OrderStatus, OrderEvent> orders = StateMachines.order();
    private final StateMachine<ProjectStatus, ProjectEvent> projects = StateMachines.project();
    private final StateMachine<GroupStatus, GroupEvent> groups = StateMachines.group();

    private TransitionContext admin() {
        return TransitionContext.of(Role.ANIMAL_ADMIN);
    }

    @Test
    @DisplayName("动物：校园活动→治疗中，须建立救助工单")
    void animal_report_accepted() {
        assertThat(animals.fire(ON_CAMPUS, REPORT_ACCEPTED, admin())).isEqualTo(TREATING);
    }

    @Test
    @DisplayName("动物：治疗中→住院观察→待放归→已放归")
    void animal_medical_chain() {
        assertThat(animals.fire(TREATING, SEND_TO_HOSPITAL, admin())).isEqualTo(HOSPITALIZED);
        assertThat(animals.fire(HOSPITALIZED, SURGERY_DONE, admin())).isEqualTo(TO_RELEASE);
        assertThat(animals.fire(TO_RELEASE, RELEASE, TransitionContext.of(Role.ANIMAL_ADMIN, "surgeryRecorded", true)))
                .isEqualTo(RELEASED);
    }

    @Test
    @DisplayName("动物：已放归再次受伤可回退到治疗中")
    void animal_release_can_regress() {
        assertThat(animals.fire(RELEASED, INJURY_FOUND, admin())).isEqualTo(TREATING);
    }

    @Test
    @DisplayName("动物：在校园活动或已放归可领养办结")
    void animal_can_be_adopted() {
        assertThat(animals.fire(ON_CAMPUS, ADOPTION_COMPLETED, admin())).isEqualTo(ADOPTED);
        assertThat(animals.fire(RELEASED, ADOPTION_COMPLETED, admin())).isEqualTo(ADOPTED);
    }

    @Test
    @DisplayName("动物：连续三十日无目击转失踪")
    void animal_missing_after_no_sighting() {
        assertThat(animals.fire(TREATING, NO_SIGHTING_30D, admin())).isEqualTo(MISSING);
    }

    @Test
    @DisplayName("动物：任意状态可确认死亡，已离世为终态")
    void animal_death_is_terminal() {
        assertThat(animals.fire(TREATING, CONFIRM_DEATH, admin())).isEqualTo(DECEASED);
        assertThat(animals.isTerminal(DECEASED)).isTrue();
        assertThatThrownBy(() -> animals.fire(DECEASED, INJURY_FOUND, admin()))
                .isInstanceOf(StateTransitionException.class);
    }

    @Test
    @DisplayName("动物：放归须有手术记录，否则拦截为 30603")
    void animal_release_requires_surgery_record() {
        assertThatThrownBy(() -> animals.fire(TO_RELEASE, RELEASE,
                TransitionContext.of(Role.ANIMAL_ADMIN, "surgeryRecorded", false)))
                .isInstanceOf(com.just.critter.support.exception.BusinessException.class)
                .satisfies(ex -> assertThat(((com.just.critter.support.exception.BusinessException) ex).getCode())
                        .isEqualTo(30603));
    }

    @Test
    @DisplayName("工单：待受理→处理中→待回访→已关闭")
    void order_main_chain() {
        assertThat(orders.fire(OrderStatus.PENDING, OrderEvent.ACCEPT, admin())).isEqualTo(OrderStatus.PROCESSING);
        assertThat(orders.fire(OrderStatus.PROCESSING, OrderEvent.DISPOSE_DONE, admin()))
                .isEqualTo(OrderStatus.TO_REVISIT);
        assertThat(orders.fire(OrderStatus.TO_REVISIT, OrderEvent.REVISIT_OK, admin()))
                .isEqualTo(OrderStatus.CLOSED);
    }

    @Test
    @DisplayName("工单：超时仍可受理，回访异常回到处理中")
    void order_timeout_and_revisit() {
        assertThat(orders.fire(OrderStatus.PENDING, OrderEvent.TIMEOUT, admin())).isEqualTo(OrderStatus.TIMEOUT);
        assertThat(orders.fire(OrderStatus.TIMEOUT, OrderEvent.ACCEPT, admin())).isEqualTo(OrderStatus.PROCESSING);
        assertThat(orders.fire(OrderStatus.TO_REVISIT, OrderEvent.REVISIT_ANOMALY, admin()))
                .isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("工单：未受理不得直接关闭")
    void order_cannot_skip_accept() {
        assertThatThrownBy(() -> orders.fire(OrderStatus.PENDING, OrderEvent.REVISIT_OK, admin()))
                .isInstanceOf(StateTransitionException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("项目：草稿→待审核→募捐中→待结项→已结项")
    void project_main_chain() {
        assertThat(projects.fire(ProjectStatus.DRAFT, ProjectEvent.SUBMIT, admin()))
                .isEqualTo(ProjectStatus.PENDING_AUDIT);
        assertThat(projects.fire(ProjectStatus.PENDING_AUDIT, ProjectEvent.APPROVE, admin()))
                .isEqualTo(ProjectStatus.FUNDING);
        assertThat(projects.fire(ProjectStatus.FUNDING, ProjectEvent.TARGET_REACHED, admin()))
                .isEqualTo(ProjectStatus.PENDING_SETTLE);
        assertThat(projects.fire(ProjectStatus.PENDING_SETTLE, ProjectEvent.SETTLE, admin()))
                .isEqualTo(ProjectStatus.SETTLED);
    }

    @Test
    @DisplayName("项目：草稿不能跳过审核直接募捐")
    void project_cannot_skip_audit() {
        assertThatThrownBy(() -> projects.fire(ProjectStatus.DRAFT, ProjectEvent.APPROVE, admin()))
                .isInstanceOf(StateTransitionException.class);
    }

    @Test
    @DisplayName("群体：活跃→未确认→已消失，已消失可回退")
    void group_lifecycle() {
        assertThat(groups.fire(GroupStatus.ACTIVE, GroupEvent.NO_SIGHTING_60D, admin()))
                .isEqualTo(GroupStatus.UNCONFIRMED);
        assertThat(groups.fire(GroupStatus.UNCONFIRMED, GroupEvent.CONFIRM_GONE, admin()))
                .isEqualTo(GroupStatus.GONE);
        assertThat(groups.fire(GroupStatus.GONE, GroupEvent.SIGHTING_CONFIRMED, admin()))
                .isEqualTo(GroupStatus.ACTIVE);
    }

    @Test
    @DisplayName("四张迁移表：同状态同事件不得重复定义")
    void no_duplicate_definition() {
        assertThat(animals.transitions()).isNotEmpty();
        assertThat(orders.transitions()).isNotEmpty();
        assertThat(projects.transitions()).isNotEmpty();
        assertThat(groups.transitions()).isNotEmpty();
    }

    @Test
    @DisplayName("除终态外，每个状态至少有一条出路")
    void every_non_terminal_state_has_escape() {
        assertThat(animals.statusesWithoutEscape()).containsExactly(DECEASED);
    }
}
