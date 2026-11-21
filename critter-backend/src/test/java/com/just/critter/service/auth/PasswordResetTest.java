package com.just.critter.service.auth;

import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.AuthDtos;
import com.just.critter.domain.model.SysUser;
import com.just.critter.mapper.RosterMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.mapper.SysUserRoleMapper;
import com.just.critter.service.support.RolePermissionService;
import com.just.critter.support.cache.KeyValueStore;
import com.just.critter.support.cache.MemoryKeyValueStore;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.AesCipher;
import com.just.critter.support.security.JwtService;
import com.just.critter.support.security.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 找回密码（接口设计 §6.1 第 6—7 项）：
 * 验证码 5 分钟有效走 KeyValueStore；错误计数与登录共用，5 次锁 10 分钟；
 * 成功后写 min-iat 让旧令牌整体失效。
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetTest {

    @Mock
    private SysUserMapper users;
    @Mock
    private SysUserRoleMapper userRoles;
    @Mock
    private RosterMapper roster;
    @Mock
    private JwtService jwt;
    @Mock
    private AuditLogService audit;
    @Mock
    private RolePermissionService rolePermissions;

    private final KeyValueStore store = new MemoryKeyValueStore();
    private final PasswordHasher hasher = new PasswordHasher();
    private final CritterProperties properties = new CritterProperties();

    private AuthService service;
    private SysUser student;

    @BeforeEach
    void setUp() {
        properties.getSecurity().setAesKey("qyWL2026CritterAESKey0123456789==");
        properties.getRegister().setCodeTtlSeconds(300);
        properties.getRegister().setExposeVerificationCode(true);
        properties.getLogin().setMaxFailures(5);
        properties.getLogin().setLockSeconds(600);
        service = new AuthService(users, userRoles, roster, hasher, new AesCipher(properties), jwt,
                store, audit, properties, rolePermissions);

        student = new SysUser();
        student.setId(1001L);
        student.setUserNo("2023019001");
        student.setEmail("2023019001@stu.just.edu.cn");
        student.setStatus("ACTIVE");
        student.setPrimaryRole("STUDENT");
        student.setPasswordHash(hasher.hash("Oldpass2026"));
    }

    @Test
    @DisplayName("发码：邮箱脱敏、TTL 300 秒、演示环境回传 devCode")
    void request_code_masks_email_and_returns_dev_code() {
        when(users.selectOne(any())).thenReturn(student);

        AuthDtos.ResetCodeResponse response =
                service.requestResetCode(new AuthDtos.ResetCodeRequest("2023019001"));

        assertThat(response.userNo()).isEqualTo("2023019001");
        assertThat(response.emailMasked()).isEqualTo("2***@stu.just.edu.cn");
        assertThat(response.codeTtlSeconds()).isEqualTo(300);
        assertThat(response.devCode()).hasSize(6);
        assertThat(store.get("pwd:code:2023019001")).contains(response.devCode());
    }

    @Test
    @DisplayName("账号不存在：30410，不发码")
    void request_code_unknown_account_is_30410() {
        when(users.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.requestResetCode(new AuthDtos.ResetCodeRequest("9999999999")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("禁用账号不发码：30104，避免用重置密码绕过禁用")
    void request_code_disabled_account_is_30104() {
        student.setStatus("DISABLED");
        when(users.selectOne(any())).thenReturn(student);

        assertThatThrownBy(() -> service.requestResetCode(new AuthDtos.ResetCodeRequest("2023019001")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED.getCode());
    }

    @Test
    @DisplayName("弱密码：30301，且不消耗验证码次数")
    void reset_rejects_weak_password() {
        assertThatThrownBy(() -> service.resetPassword(
                new AuthDtos.ResetPasswordRequest("2023019001", "123456", "abc"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        verify(users, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("验证码错：30105，剩余次数递减（与登录共用计数）")
    void reset_with_wrong_code_returns_30105() {
        when(users.selectOne(any())).thenReturn(student);
        store.set("pwd:code:2023019001", "111111", Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.resetPassword(
                new AuthDtos.ResetPasswordRequest("2023019001", "222222", "Newpass2026"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException biz = (BusinessException) ex;
                    assertThat(biz.getCode()).isEqualTo(ErrorCode.VERIFY_CODE_INVALID.getCode());
                    assertThat(String.valueOf(biz.getData())).contains("remainingAttempts=4");
                });

        // 第二次错误：剩余次数应降到 3，说明账号维度的失败计数确实在累加
        assertThatThrownBy(() -> service.resetPassword(
                new AuthDtos.ResetPasswordRequest("2023019001", "333333", "Newpass2026"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(String.valueOf(((BusinessException) ex).getData()))
                        .contains("remainingAttempts=3"));
    }

    @Test
    @DisplayName("验证码连错 5 次后锁定：第 6 次 30103")
    void reset_locks_after_five_failures() {
        when(users.selectOne(any())).thenReturn(student);
        store.set("pwd:code:2023019001", "111111", Duration.ofMinutes(5));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.resetPassword(
                    new AuthDtos.ResetPasswordRequest("2023019001", "222222", "Newpass2026"), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.VERIFY_CODE_INVALID.getCode());
        }

        assertThatThrownBy(() -> service.resetPassword(
                new AuthDtos.ResetPasswordRequest("2023019001", "111111", "Newpass2026"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode());
    }

    @Test
    @DisplayName("正确验证码：更新密码哈希、清计数、写 min-iat、删除验证码")
    void reset_success_updates_hash_and_invalidates_tokens() {
        when(users.selectOne(any())).thenReturn(student);
        store.set("pwd:code:2023019001", "654321", Duration.ofMinutes(5));

        service.resetPassword(new AuthDtos.ResetPasswordRequest("2023019001", "654321", "Newpass2026"),
                "127.0.0.1");

        verify(users).update(isNull(), any());
        assertThat(store.get("pwd:code:2023019001")).isEmpty();
        assertThat(store.get("auth:min-iat:1001")).isPresent();
        verify(audit).successAs(1001L, "STUDENT", "PASSWORD_RESET", "sys_user", "2023019001", null,
                java.util.Map.of("note", "找回密码后旧令牌全部失效"));
    }
}
