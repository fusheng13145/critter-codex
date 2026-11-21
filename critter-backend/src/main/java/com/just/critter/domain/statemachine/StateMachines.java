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
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 四类对象的状态迁移表（需求分析说明书 §8）。所有表在此集中定义，业务代码不写 if-else。
 */
public final class StateMachines {

    private static final Set<Role> ADMIN = Set.of(Role.ANIMAL_ADMIN);
    private static final Set<Role> ADMIN_OR_JOB = Set.of(Role.ANIMAL_ADMIN, Role.SYSTEM);

    private static final StateMachine<AnimalStatus, AnimalEvent> ANIMAL = buildAnimal();
    private static final StateMachine<OrderStatus, OrderEvent> ORDER = buildOrder();
    private static final StateMachine<ProjectStatus, ProjectEvent> PROJECT = buildProject();
    private static final StateMachine<GroupStatus, GroupEvent> GROUP = buildGroup();

    private StateMachines() {
    }

    public static StateMachine<AnimalStatus, AnimalEvent> animal() {
        return ANIMAL;
    }

    public static StateMachine<OrderStatus, OrderEvent> order() {
        return ORDER;
    }

    public static StateMachine<ProjectStatus, ProjectEvent> project() {
        return PROJECT;
    }

    public static StateMachine<GroupStatus, GroupEvent> group() {
        return GROUP;
    }

    private static StateMachine<AnimalStatus, AnimalEvent> buildAnimal() {
        Precondition releaseNeedsSurgeryRecord = context -> {
            if (!context.flag("surgeryRecorded")) {
                throw new BusinessException(ErrorCode.TNR_RECORD_MISSING);
            }
        };

        List<Transition<AnimalStatus, AnimalEvent>> list = new ArrayList<>();
        list.add(animal(AnimalStatus.ON_CAMPUS, AnimalEvent.REPORT_ACCEPTED, AnimalStatus.TREATING));
        list.add(animal(AnimalStatus.TREATING, AnimalEvent.SEND_TO_HOSPITAL, AnimalStatus.HOSPITALIZED));
        list.add(animal(AnimalStatus.HOSPITALIZED, AnimalEvent.SURGERY_DONE, AnimalStatus.TO_RELEASE));
        list.add(new Transition<>(AnimalStatus.TO_RELEASE, AnimalEvent.RELEASE, AnimalStatus.RELEASED,
                ADMIN, releaseNeedsSurgeryRecord));
        list.add(animal(AnimalStatus.RELEASED, AnimalEvent.INJURY_FOUND, AnimalStatus.TREATING));
        list.add(animal(AnimalStatus.ON_CAMPUS, AnimalEvent.ADOPTION_COMPLETED, AnimalStatus.ADOPTED));
        list.add(animal(AnimalStatus.RELEASED, AnimalEvent.ADOPTION_COMPLETED, AnimalStatus.ADOPTED));

        for (AnimalStatus status : activityStatuses()) {
            list.add(animal(status, AnimalEvent.NO_SIGHTING_30D, AnimalStatus.MISSING));
        }
        for (AnimalStatus status : EnumSet.complementOf(EnumSet.of(AnimalStatus.DECEASED))) {
            list.add(animal(status, AnimalEvent.CONFIRM_DEATH, AnimalStatus.DECEASED));
        }
        return new StateMachine<>(ErrorCode.STATE_TRANSITION_INVALID, list, EnumSet.allOf(AnimalStatus.class));
    }

    private static List<AnimalStatus> activityStatuses() {
        return List.of(AnimalStatus.ON_CAMPUS, AnimalStatus.TREATING, AnimalStatus.HOSPITALIZED,
                AnimalStatus.TO_RELEASE, AnimalStatus.RELEASED, AnimalStatus.ADOPTED);
    }

    private static Transition<AnimalStatus, AnimalEvent> animal(
            AnimalStatus from, AnimalEvent event, AnimalStatus to) {
        return new Transition<>(from, event, to, ADMIN, null);
    }

    private static StateMachine<OrderStatus, OrderEvent> buildOrder() {
        List<Transition<OrderStatus, OrderEvent>> list = new ArrayList<>();
        list.add(new Transition<>(OrderStatus.PENDING, OrderEvent.ACCEPT, OrderStatus.PROCESSING, ADMIN, null));
        list.add(new Transition<>(OrderStatus.PENDING, OrderEvent.TIMEOUT, OrderStatus.TIMEOUT, ADMIN_OR_JOB, null));
        list.add(new Transition<>(OrderStatus.TIMEOUT, OrderEvent.ACCEPT, OrderStatus.PROCESSING, ADMIN, null));
        list.add(new Transition<>(OrderStatus.PROCESSING, OrderEvent.DISPOSE_DONE, OrderStatus.TO_REVISIT, ADMIN, null));
        list.add(new Transition<>(OrderStatus.PROCESSING, OrderEvent.CANNOT_DISPOSE, OrderStatus.CLOSED, ADMIN, null));
        list.add(new Transition<>(OrderStatus.TO_REVISIT, OrderEvent.REVISIT_OK, OrderStatus.CLOSED, ADMIN, null));
        list.add(new Transition<>(OrderStatus.TO_REVISIT, OrderEvent.REVISIT_ANOMALY, OrderStatus.PROCESSING, ADMIN, null));
        return new StateMachine<>(ErrorCode.ORDER_STATE_INVALID, list, EnumSet.allOf(OrderStatus.class));
    }

    private static StateMachine<ProjectStatus, ProjectEvent> buildProject() {
        List<Transition<ProjectStatus, ProjectEvent>> list = new ArrayList<>();
        list.add(project(ProjectStatus.DRAFT, ProjectEvent.SUBMIT, ProjectStatus.PENDING_AUDIT));
        list.add(project(ProjectStatus.PENDING_AUDIT, ProjectEvent.APPROVE, ProjectStatus.FUNDING));
        list.add(project(ProjectStatus.PENDING_AUDIT, ProjectEvent.REJECT, ProjectStatus.REJECTED));
        list.add(project(ProjectStatus.REJECTED, ProjectEvent.RESUBMIT, ProjectStatus.PENDING_AUDIT));
        list.add(project(ProjectStatus.FUNDING, ProjectEvent.TARGET_REACHED, ProjectStatus.PENDING_SETTLE));
        list.add(project(ProjectStatus.FUNDING, ProjectEvent.DEADLINE_REACHED, ProjectStatus.PENDING_SETTLE));
        list.add(project(ProjectStatus.FUNDING, ProjectEvent.DEADLINE_MISSED, ProjectStatus.ENDED));
        list.add(project(ProjectStatus.PENDING_SETTLE, ProjectEvent.SETTLE, ProjectStatus.SETTLED));
        return new StateMachine<>(ErrorCode.STATE_TRANSITION_INVALID, list, EnumSet.allOf(ProjectStatus.class));
    }

    private static Transition<ProjectStatus, ProjectEvent> project(
            ProjectStatus from, ProjectEvent event, ProjectStatus to) {
        return new Transition<>(from, event, to, ADMIN, null);
    }

    private static StateMachine<GroupStatus, GroupEvent> buildGroup() {
        List<Transition<GroupStatus, GroupEvent>> list = new ArrayList<>();
        list.add(new Transition<>(GroupStatus.ACTIVE, GroupEvent.NO_SIGHTING_60D, GroupStatus.UNCONFIRMED,
                ADMIN_OR_JOB, null));
        list.add(new Transition<>(GroupStatus.UNCONFIRMED, GroupEvent.SIGHTING_CONFIRMED, GroupStatus.ACTIVE,
                ADMIN, null));
        list.add(new Transition<>(GroupStatus.UNCONFIRMED, GroupEvent.CONFIRM_GONE, GroupStatus.GONE, ADMIN, null));
        list.add(new Transition<>(GroupStatus.GONE, GroupEvent.SIGHTING_CONFIRMED, GroupStatus.ACTIVE, ADMIN, null));
        return new StateMachine<>(ErrorCode.STATE_TRANSITION_INVALID, list, EnumSet.allOf(GroupStatus.class));
    }
}
