package com.just.critter.service.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.UserDtos;
import com.just.critter.domain.model.SysUser;
import com.just.critter.domain.model.SysUserRole;
import com.just.critter.mapper.RosterMapper;
import com.just.critter.mapper.SysLogMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.mapper.SysUserRoleMapper;
import com.just.critter.service.auth.AuthService;
import com.just.critter.service.support.RolePermissionService;
import com.just.critter.support.cache.KeyValueStore;
import com.just.critter.support.cache.MemoryKeyValueStore;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.AesCipher;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.security.JwtService;
import com.just.critter.support.security.PasswordHasher;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户治理主链路（FR-U-04 / FR-U-06 / FR-SYS-03）：
 * 身份申请的重复校验、审批（含临时授权 expireHours）、禁用必须给原因并作废旧令牌。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SysUserMapper users;
    @Mock
    private SysUserRoleMapper userRoles;
    @Mock
    private SysLogMapper logs;
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

    private UserService service;
    private SysUser student;

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mockito 单测需要手动注册实体的列缓存（lambda wrapper 依赖它）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
    }

    @BeforeEach
    void setUp() {
        properties.getSecurity().setAesKey("qyWL2026CritterAESKey0123456789==");
        properties.getSecurity().setJwtSecret("qingyan-wanling-test-secret-key-for-unit-only");

        AuthService authService = new AuthService(users, userRoles, roster, hasher,
                new AesCipher(properties), jwt, store, audit, properties, rolePermissions);
        service = new UserService(users, userRoles, logs, hasher, new AesCipher(properties),
                authService, audit, store);

        student = new SysUser();
        student.setId(1001L);
        student.setUserNo("2023019001");
        student.setEmail("2023019001@stu.just.edu.cn");
        student.setStatus("ACTIVE");
        student.setPrimaryRole("STUDENT");
        student.setDept("计算机学院");
        student.setNickname("青檐一号");
        student.setPasswordHash(hasher.hash("Qy@2026"));

        AuthContext.set(new CurrentUser(1001L, "2023019001", "青檐一号", "STUDENT",
                Set.of("STUDENT"), "计算机学院", "jti-test", Instant.now().plusSeconds(600)));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private SysUserRole pendingApplication(String roleCode) {
        SysUserRole row = new SysUserRole();
        row.setId(88L);
        row.setUserId(1001L);
        row.setRoleCode(roleCode);
        row.setStatus("PENDING");
        row.setApplyReason("希望长期参与校园猫的照顾与投喂");
        return row;
    }

    @Test
    @DisplayName("申请志愿者：落 PENDING 行，不提前拿到角色")
    void apply_role_creates_pending_row() {
        when(users.selectById(1001L)).thenReturn(student);
        when(userRoles.selectList(any())).thenReturn(List.of());
        when(userRoles.selectCount(any())).thenReturn(0L);
        when(userRoles.insert(any(SysUserRole.class))).thenAnswer(inv -> {
            ((SysUserRole) inv.getArgument(0)).setId(90L);
            return 1;
        });

        UserDtos.ApplyRoleResponse response = service.applyRole(1001L,
                new UserDtos.ApplyRoleRequest("VOLUNTEER", "希望长期参与校园猫的照顾与投喂", "周三下午"));

        assertThat(response.status()).isEqualTo("PENDING");
        ArgumentCaptor<SysUserRole> captor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoles).insert(captor.capture());
        assertThat(captor.getValue().getRoleCode()).isEqualTo("VOLUNTEER");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getGrantedAt()).isNull();
    }

    @Test
    @DisplayName("重复申请：已拥有该身份被拒")
    void apply_role_rejected_when_already_granted() {
        when(users.selectById(1001L)).thenReturn(student);
        SysUserRole granted = pendingApplication("VOLUNTEER");
        granted.setStatus("GRANTED");
        granted.setGrantedAt(java.time.LocalDateTime.now());
        when(userRoles.selectList(any())).thenReturn(List.of(granted));

        assertThatThrownBy(() -> service.applyRole(1001L,
                new UserDtos.ApplyRoleRequest("VOLUNTEER", "希望长期参与校园猫的照顾与投喂", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已拥有该身份");
    }

    @Test
    @DisplayName("审批通过 + 临时授权 24 小时：expire_at 落库，rolesOf 到期即失效")
    void review_with_expire_hours_sets_expire_at() {
        SysUserRole row = pendingApplication("VOLUNTEER");
        SysUserRole after = pendingApplication("VOLUNTEER");
        after.setStatus("GRANTED");
        after.setGrantedAt(java.time.LocalDateTime.now());
        after.setExpireAt(java.time.LocalDateTime.now().plusHours(24));
        when(userRoles.selectById(88L)).thenReturn(row, after);
        when(userRoles.updateById(any(SysUserRole.class))).thenReturn(1);

        UserDtos.ApplicationView view = service.reviewApplication(88L, true, null, 24);

        ArgumentCaptor<SysUserRole> captor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoles).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("GRANTED");
        assertThat(captor.getValue().getExpireAt()).isNotNull();
        assertThat(view.expireAt()).isNotNull();
        verify(audit).success(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("永久授予：不传 expireHours 时 expire_at 为空")
    void review_without_expire_hours_grants_permanently() {
        SysUserRole row = pendingApplication("ADOPTER");
        SysUserRole after = pendingApplication("ADOPTER");
        after.setStatus("GRANTED");
        when(userRoles.selectById(88L)).thenReturn(row, after);
        when(userRoles.updateById(any(SysUserRole.class))).thenReturn(1);

        service.reviewApplication(88L, true, null, null);

        ArgumentCaptor<SysUserRole> captor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoles).updateById(captor.capture());
        assertThat(captor.getValue().getExpireAt()).isNull();
    }

    @Test
    @DisplayName("临时授权超 720 小时：30301 拒绝")
    void review_rejects_overlong_expire_hours() {
        when(userRoles.selectById(88L)).thenReturn(pendingApplication("VOLUNTEER"));

        assertThatThrownBy(() -> service.reviewApplication(88L, true, null, 1000))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(String.valueOf(((BusinessException) ex).getData()))
                        .contains("expireHours"));
    }

    @Test
    @DisplayName("重复审批：30407 审核冲突")
    void review_twice_conflicts() {
        SysUserRole granted = pendingApplication("VOLUNTEER");
        granted.setStatus("GRANTED");
        when(userRoles.selectById(88L)).thenReturn(granted);

        assertThatThrownBy(() -> service.reviewApplication(88L, true, null, null))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.AUDIT_CONFLICT.getCode());
    }

    @Test
    @DisplayName("禁用账号：必须给不少于 5 字原因，成功后写 min-iat 让旧令牌失效")
    void disable_requires_reason_and_invalidates_tokens() {
        when(users.selectById(1001L)).thenReturn(student);
        when(users.update(any(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.updateStatus(1001L,
                new UserDtos.StatusUpdateRequest("DISABLED", "太短")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不少于 5 字");

        service.updateStatus(1001L,
                new UserDtos.StatusUpdateRequest("DISABLED", "多次违规发布不实信息"));

        assertThat(store.get("auth:min-iat:1001")).isPresent();
        verify(audit).success(any(), any(), any(), any(), any());
    }
}
