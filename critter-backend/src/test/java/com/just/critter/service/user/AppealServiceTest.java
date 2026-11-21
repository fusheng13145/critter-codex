package com.just.critter.service.user;

import com.just.critter.controller.dto.AppealDtos;
import com.just.critter.domain.model.SysAppeal;
import com.just.critter.domain.model.SysUser;
import com.just.critter.mapper.SysAppealMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 账号禁申诉：只有禁用态可申诉，重复提交幂等，账号不存在 30410、非禁用 30402。 */
@ExtendWith(MockitoExtension.class)
class AppealServiceTest {

    @Mock
    private SysAppealMapper appeals;
    @Mock
    private SysUserMapper users;
    @Mock
    private AuditLogService audit;

    private AppealService service;
    private SysUser disabled;

    @BeforeEach
    void setUp() {
        service = new AppealService(appeals, users, audit);
        disabled = new SysUser();
        disabled.setId(1105L);
        disabled.setUserNo("2023010105");
        disabled.setStatus("DISABLED");
    }

    @Test
    @DisplayName("禁用账号首次申诉：落库 PENDING 并留痕")
    void submit_creates_pending_appeal() {
        when(users.selectOne(any())).thenReturn(disabled);
        when(appeals.selectOne(any())).thenReturn(null);
        when(appeals.insert(any(SysAppeal.class))).thenAnswer(invocation -> {
            SysAppeal row = invocation.getArgument(0);
            row.setId(9L);
            return 1;
        });

        AppealDtos.AppealResponse response = service.submit(
                new AppealDtos.AppealRequest("2023010105", "账号被误判，申请复核", "13800000000"), "127.0.0.1");

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.createdAt()).isNotNull();

        ArgumentCaptor<SysAppeal> captor = ArgumentCaptor.forClass(SysAppeal.class);
        verify(appeals).insert(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("账号被误判，申请复核");
        assertThat(captor.getValue().getIp()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("已有待处理申诉：不重复建单，回原记录")
    void submit_is_idempotent_when_pending_exists() {
        SysAppeal existing = new SysAppeal();
        existing.setId(3L);
        existing.setUserNo("2023010105");
        existing.setStatus("PENDING");
        existing.setCreatedAt(LocalDateTime.now());

        when(users.selectOne(any())).thenReturn(disabled);
        when(appeals.selectOne(any())).thenReturn(existing);

        AppealDtos.AppealResponse response = service.submit(
                new AppealDtos.AppealRequest("2023010105", "再补一条材料", null), "127.0.0.1");

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.message()).contains("无需重复提交");
        verify(appeals, never()).insert(any(SysAppeal.class));
    }

    @Test
    @DisplayName("正常账号申诉：30402，无需申诉")
    void submit_rejects_active_account() {
        disabled.setStatus("ACTIVE");
        when(users.selectOne(any())).thenReturn(disabled);

        assertThatThrownBy(() -> service.submit(
                new AppealDtos.AppealRequest("2023010105", "我其实没被禁用", null), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.STATE_TRANSITION_INVALID.getCode());
    }

    @Test
    @DisplayName("账号不存在：30410")
    void submit_unknown_account_is_30410() {
        when(users.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.submit(
                new AppealDtos.AppealRequest("9999999999", "查无此人也要申诉", null), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND.getCode());
    }
}
