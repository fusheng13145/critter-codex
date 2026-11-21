package com.just.critter.service.support;

import com.just.critter.controller.dto.RoleDtos;
import com.just.critter.domain.model.SysPermission;
import com.just.critter.domain.model.SysRole;
import com.just.critter.mapper.SysPermissionMapper;
import com.just.critter.mapper.SysRoleMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在线配置角色权限：预览不落库、落库生效、自锁拦截、未知权限码拒绝。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private SysRoleMapper roles;
    @Mock
    private SysPermissionMapper permissions;
    @Mock
    private RolePermissionService rolePermissions;
    @Mock
    private AuditLogService audit;

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService(roles, permissions, rolePermissions, audit);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("角色不存在：30410")
    void unknown_role_is_30410() {
        when(roles.selectById(6L)).thenReturn(null);

        assertThatThrownBy(() -> service.updatePermissions(6L, new RoleDtos.PermissionUpdateRequest(
                List.of("home"), List.of(), true)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("预览只算差异，不写库")
    void preview_does_not_persist() {
        when(roles.selectById(2L)).thenReturn(role(2L, "STUDENT", "注册学生"));
        when(rolePermissions.storedPerms("STUDENT"))
                .thenReturn(List.of("menu:home", "animal:read"));
        when(permissions.selectList(any())).thenReturn(List.of(permission("animal:read")));

        RoleDtos.PermissionDiff diff = service.updatePermissions(2L, new RoleDtos.PermissionUpdateRequest(
                List.of("home", "stats"), List.of("animal:read"), true));

        assertThat(diff.applied()).isFalse();
        assertThat(diff.addedMenus()).containsExactly("menu:stats");
        assertThat(diff.removedMenus()).isEmpty();
        assertThat(diff.addedApis()).isEmpty();
        assertThat(diff.removedApis()).isEmpty();
        verify(rolePermissions, never()).replace(any(), anyCollection());
    }

    @Test
    @DisplayName("确认落库：合并菜单与接口后一次写入")
    void apply_writes_merged_permissions() {
        when(roles.selectById(2L)).thenReturn(role(2L, "STUDENT", "注册学生"));
        when(rolePermissions.storedPerms("STUDENT")).thenReturn(List.of("menu:home"));
        when(permissions.selectList(any())).thenReturn(List.of(permission("animal:read")));

        RoleDtos.PermissionDiff diff = service.updatePermissions(2L, new RoleDtos.PermissionUpdateRequest(
                List.of("home", "stats"), List.of("animal:read"), false));

        assertThat(diff.applied()).isTrue();
        verify(rolePermissions).replace("STUDENT", List.of("menu:home", "menu:stats", "animal:read"));
        verify(audit).success(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("系统管理员摘掉后台菜单：30301，禁止自锁")
    void system_admin_cannot_drop_admin_menu() {
        when(roles.selectById(6L)).thenReturn(role(6L, "SYS_ADMIN", "系统管理员"));
        when(permissions.selectList(any())).thenReturn(List.of(permission("role:manage")));

        assertThatThrownBy(() -> service.updatePermissions(6L, new RoleDtos.PermissionUpdateRequest(
                List.of("home", "stats"), List.of("role:manage"), true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    @DisplayName("未知接口权限码：30301，不落库")
    void unknown_api_code_is_rejected() {
        when(roles.selectById(2L)).thenReturn(role(2L, "STUDENT", "注册学生"));
        when(permissions.selectList(any())).thenReturn(List.of(permission("animal:read")));

        assertThatThrownBy(() -> service.updatePermissions(2L, new RoleDtos.PermissionUpdateRequest(
                List.of("home"), List.of("animal:reed"), true)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        verify(rolePermissions, never()).replace(any(), anyCollection());
    }

    @Test
    @DisplayName("不能摘掉自己当前角色的 role:manage")
    void caller_cannot_lock_self_out_of_role_manage() {
        when(roles.selectById(5L)).thenReturn(role(5L, "ANIMAL_ADMIN", "动物管理员"));
        when(permissions.selectList(any())).thenReturn(List.of(permission("animal:read")));
        AuthContext.set(new CurrentUser(7L, "2023010101", "林照野", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "计算机学院", "jti", Instant.now().plusSeconds(600)));

        assertThatThrownBy(() -> service.updatePermissions(5L, new RoleDtos.PermissionUpdateRequest(
                List.of("medical"), List.of("animal:read"), false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException biz = (BusinessException) ex;
                    assertThat(biz.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
                    // 具体原因在字段错误里，前端按 field=apis 标红
                    assertThat(String.valueOf(biz.getData())).contains("role:manage");
                });
    }

    private static SysRole role(long id, String code, String name) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(name);
        return role;
    }

    private static SysPermission permission(String code) {
        SysPermission permission = new SysPermission();
        permission.setPermCode(code);
        permission.setType("API");
        return permission;
    }
}
