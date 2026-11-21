package com.just.critter.adopt;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.repository.AdoptionPlanRepository;
import com.just.critter.adopt.service.AdoptionPlanCommand;
import com.just.critter.adopt.service.AdoptionPlanService;
import com.just.critter.adopt.service.AdoptionPlanWrite;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoptionPlanServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T06:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private AdoptionPlanRepository repository;
    private AuditLogService audit;
    private AdoptionPlanService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdoptionPlanRepository.class);
        audit = mock(AuditLogService.class);
        service = new AdoptionPlanService(repository, audit, CLOCK);
    }

    @Test
    @DisplayName("公开列表只包含启用方案，停用方案不对外暴露")
    void listEnabledPlansExcludesDisabledPlans() {
        AdoptionPlan enabled = new AdoptionPlan(
                1L, "测试月度方案", AdoptionPeriod.MONTH, 3000, "测试权益", true);
        AdoptionPlan disabled = new AdoptionPlan(
                2L, "测试季度方案", AdoptionPeriod.QUARTER, 9000, "测试权益", false);
        when(repository.findAll()).thenReturn(List.of(enabled, disabled));

        assertThat(service.listEnabledPlans()).containsExactly(enabled);
    }

    @Test
    @DisplayName("管理员列表包含停用方案")
    void adminListIncludesDisabledPlans() {
        AdoptionPlan enabled = new AdoptionPlan(
                1L, "月度", AdoptionPeriod.MONTH, 3000, "权益", true);
        AdoptionPlan disabled = new AdoptionPlan(
                2L, "季度", AdoptionPeriod.QUARTER, 9000, "权益", false);
        when(repository.findAll()).thenReturn(List.of(enabled, disabled));

        assertThat(service.listPlans(true)).containsExactly(enabled, disabled);
    }

    @Test
    @DisplayName("新增方案：校验后写入并返回方案")
    void createsPlan() {
        AdoptionPlan created = new AdoptionPlan(
                9L, "校园月度守护", AdoptionPeriod.MONTH, 3000, "每月一次照片", true);
        when(repository.insert(any())).thenReturn(created);

        AdoptionPlan result = service.create(new AdoptionPlanCommand(
                " 校园月度守护 ", "MONTH", 3000, " 每月一次照片 ", null), 1L);

        assertThat(result).isEqualTo(created);
        ArgumentCaptor<AdoptionPlanWrite> write = ArgumentCaptor.forClass(AdoptionPlanWrite.class);
        verify(repository).insert(write.capture());
        assertThat(write.getValue().planName()).isEqualTo("校园月度守护");
        assertThat(write.getValue().period()).isEqualTo(AdoptionPeriod.MONTH);
        assertThat(write.getValue().benefits()).isEqualTo("每月一次照片");
        assertThat(write.getValue().enabled()).isTrue();
        assertThat(write.getValue().createdBy()).isEqualTo(1L);
        verify(audit).success(eq("PLAN_CREATE"), eq("PLAN"), eq("9"), eq(null), eq(created));
    }

    @Test
    @DisplayName("新增方案：周期非法返回 30303")
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() -> service.create(new AdoptionPlanCommand(
                "方案", "YEAR", 3000, null, true), 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30303));
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("新增方案：金额非正或名称超长返回 30301")
    void rejectsInvalidFields() {
        assertThatThrownBy(() -> service.create(new AdoptionPlanCommand(
                "方案", "MONTH", 0, null, true), 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        assertThatThrownBy(() -> service.create(new AdoptionPlanCommand(
                "这是一个超过三十二个字符的认养方案名称用于测试字段长度校验必须失败",
                "MONTH", 3000, null, true), 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
    }

    @Test
    @DisplayName("编辑方案：不存在返回 30410，成功更新启停状态")
    void updatesPlan() {
        AdoptionPlan existing = new AdoptionPlan(
                2L, "季度守护", AdoptionPeriod.QUARTER, 9000, null, true);
        AdoptionPlan updated = new AdoptionPlan(
                2L, "季度守护", AdoptionPeriod.QUARTER, 9000, null, false);
        when(repository.findById(2L)).thenReturn(Optional.of(existing), Optional.of(updated));
        when(repository.update(eq(2L), any(), any())).thenReturn(1);

        AdoptionPlan result = service.update(2L, new AdoptionPlanCommand(
                "季度守护", "QUARTER", 9000, null, false), 1L);

        assertThat(result.enabled()).isFalse();
        verify(repository).update(eq(2L), any(), any());
        verify(audit).success(eq("PLAN_UPDATE"), eq("PLAN"), eq("2"), eq(existing), eq(updated));
    }

    @Test
    @DisplayName("编辑方案：记录不存在返回 30410")
    void updateMissingPlan() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new AdoptionPlanCommand(
                "方案", "MONTH", 3000, null, true), 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("编辑方案：更新 0 行返回 50002")
    void updateZeroRowsFails() {
        AdoptionPlan existing = new AdoptionPlan(
                2L, "季度守护", AdoptionPeriod.QUARTER, 9000, null, true);
        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        when(repository.update(eq(2L), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(2L, new AdoptionPlanCommand(
                "季度守护", "QUARTER", 9000, null, false), 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(50002));
    }
}
