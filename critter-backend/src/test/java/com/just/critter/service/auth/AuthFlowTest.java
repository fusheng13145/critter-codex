package com.just.critter.service.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.AuthDtos;
import com.just.critter.domain.model.BaseRoster;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 登录与注册主链路（FR-U-01 / FR-U-02，成员 A 域此前缺测的部分）：
 * 锁定计数、双令牌签发、学号 + 邮箱双校验、名单比对。
 */
@ExtendWith(MockitoExtension.class)
class AuthFlowTest {

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

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mockito 单测不会启动 MyBatis-Plus，需要手动注册实体的列缓存（同 ProjectDeadlineJobTest）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, com.just.critter.domain.model.SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, BaseRoster.class);
    }

    @BeforeEach
    void setUp() {
        properties.getSecurity().setAesKey("qyWL2026CritterAESKey0123456789==");
        properties.getSecurity().setJwtSecret("qingyan-wanling-test-secret-key-for-unit-only");
        properties.getRegister().setCodeTtlSeconds(300);
        properties.getRegister().setExposeVerificationCode(true);
        properties.getRegister().setAllowedEmailDomains("just.edu.cn,stu.just.edu.cn");
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
        student.setDept("计算机学院");
        student.setPasswordHash(hasher.hash("Qy@2026"));
    }

    private void stubTokenIssue() {
        when(jwt.issueAccess(anyLong(), anyString(), anyString(), anySet(), any()))
                .thenReturn(new JwtService.Issued("access-token", "jti-a",
                        Instant.now().plusSeconds(7200), 7200));
        when(jwt.issueRefresh(anyLong(), anyString(), anyString(), anySet(), any()))
                .thenReturn(new JwtService.Issued("refresh-token", "jti-r",
                        Instant.now().plusSeconds(604800), 604800));
        when(userRoles.selectList(any())).thenReturn(List.of());
        when(userRoles.selectCount(any())).thenReturn(0L);
        when(rolePermissions.menusFor(any()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("home", "me")));
    }

    @Test
    @DisplayName("登录成功：双令牌、角色与菜单随响应返回，失败计数清零")
    void login_success_issues_tokens_and_clears_counters() {
        stubTokenIssue();
        when(users.selectOne(any())).thenReturn(student);
        // 先制造一次失败记录，成功登录应清掉
        store.increment("login:fail:acct:2023019001", java.time.Duration.ofMinutes(30));

        AuthDtos.LoginResponse response = service.login(
                new AuthDtos.LoginRequest("2023019001", "Qy@2026"), "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(7200);
        assertThat(response.user().userNo()).isEqualTo("2023019001");
        assertThat(response.user().menus()).containsExactly("home", "me");
        assertThat(store.get("login:fail:acct:2023019001")).isEmpty();
    }

    @Test
    @DisplayName("密码错误：30101 且剩余次数递减")
    void login_wrong_password_counts_down() {
        when(users.selectOne(any())).thenReturn(student);

        for (int remaining = 4; remaining >= 3; remaining--) {
            final int expected = remaining;
            assertThatThrownBy(() -> service.login(
                    new AuthDtos.LoginRequest("2023019001", "wrong"), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(String.valueOf(((BusinessException) ex).getData()))
                            .contains("remainingAttempts=" + expected));
        }
    }

    @Test
    @DisplayName("连错 5 次锁定：第 6 次 30103 并带 lockSeconds")
    void login_locks_after_five_failures() {
        when(users.selectOne(any())).thenReturn(student);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(
                    new AuthDtos.LoginRequest("2023019001", "wrong"), "127.0.0.1"))
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.BAD_CREDENTIALS.getCode());
        }
        assertThatThrownBy(() -> service.login(
                new AuthDtos.LoginRequest("2023019001", "Qy@2026"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException biz = (BusinessException) ex;
                    assertThat(biz.getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode());
                    assertThat(String.valueOf(biz.getData())).contains("lockSeconds");
                });
    }

    @Test
    @DisplayName("IP 维度锁定：换账号也登不上（同 IP 连错 5 次）")
    void login_locks_by_ip_dimension() {
        // 前半段：陌生账号在同一 IP 连错 5 次；后半段：换成真实存在的账号，IP 锁依然生效
        when(users.selectOne(any())).thenReturn(null, null, null, null, null);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(
                    new AuthDtos.LoginRequest("9999999999", "nope"), "10.1.1.5"))
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.BAD_CREDENTIALS.getCode());
        }

        SysUser other = new SysUser();
        other.setId(1002L);
        other.setUserNo("2023019002");
        other.setStatus("ACTIVE");
        other.setPrimaryRole("STUDENT");
        other.setPasswordHash(hasher.hash("Qy@2026"));
        // IP 锁在 selectOne 之前就拦截，这个桩只是兜底（验证 30103 发生在查库前），用 lenient
        lenient().when(users.selectOne(any())).thenReturn(other);

        assertThatThrownBy(() -> service.login(
                new AuthDtos.LoginRequest("2023019002", "Qy@2026"), "10.1.1.5"))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode());
    }

    @Test
    @DisplayName("禁用账号：30104，即使密码正确")
    void login_disabled_account_is_30104() {
        student.setStatus("DISABLED");
        when(users.selectOne(any())).thenReturn(student);

        assertThatThrownBy(() -> service.login(
                new AuthDtos.LoginRequest("2023019001", "Qy@2026"), "127.0.0.1"))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED.getCode());
    }

    @Test
    @DisplayName("待验证账号：30104，提示先完成邮箱验证")
    void login_pending_account_needs_verification() {
        student.setStatus("PENDING");
        when(users.selectOne(any())).thenReturn(student);

        assertThatThrownBy(() -> service.login(
                new AuthDtos.LoginRequest("2023019001", "Qy@2026"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱验证");
    }

    @Test
    @DisplayName("注册成功：名单内学号建 PENDING 账号，验证码入缓存并回传 devCode")
    void register_success_creates_pending_user() {
        when(users.selectCount(any())).thenReturn(0L);
        BaseRoster rosterRow = new BaseRoster();
        rosterRow.setUserNo("2023010106");
        rosterRow.setDept("外国语学院");
        when(roster.selectOne(any())).thenReturn(rosterRow);
        when(users.insert(any(SysUser.class))).thenAnswer(inv -> {
            ((SysUser) inv.getArgument(0)).setId(99L);
            return 1;
        });

        AuthDtos.RegisterResponse response = service.register(new AuthDtos.RegisterRequest(
                "2023010106", "2023010106@stu.just.edu.cn", "Qy@2026aa", "何桨", null, null));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.devCode()).hasSize(6);
        assertThat(response.userNo()).isEqualTo("2023010106");
        assertThat(store.get("reg:code:2023010106@stu.just.edu.cn")).contains(response.devCode());
    }

    @Test
    @DisplayName("注册校验：外校邮箱 30301 并定位到 email 字段")
    void register_rejects_external_email_domain() {
        assertThatThrownBy(() -> service.register(new AuthDtos.RegisterRequest(
                "2023010106", "someone@qq.com", "Qy@2026aa", "何桨", null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException biz = (BusinessException) ex;
                    assertThat(biz.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
                    assertThat(String.valueOf(biz.getData())).contains("email");
                });
    }

    @Test
    @DisplayName("注册校验：学号重复 30201")
    void register_rejects_duplicate_student_no() {
        when(users.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.register(new AuthDtos.RegisterRequest(
                "2023019001", "new@stu.just.edu.cn", "Qy@2026aa", "重复学号", null, null)))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.STUDENT_NO_REGISTERED.getCode());
    }

    @Test
    @DisplayName("注册校验：名单外学号 30202（可申诉口径）")
    void register_rejects_unknown_roster() {
        when(users.selectCount(any())).thenReturn(0L);
        when(roster.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.register(new AuthDtos.RegisterRequest(
                "2099999999", "unknown@stu.just.edu.cn", "Qy@2026aa", "名单外", null, null)))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.STUDENT_NO_NOT_IN_ROSTER.getCode());
    }
}
