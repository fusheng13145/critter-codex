package com.just.critter.service.support;

import com.just.critter.domain.model.SysRolePermission;
import com.just.critter.mapper.SysRolePermissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色权限读取：以库为准、无记录回退常量；系统管理员两套角色码成对读写。
 */
@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private SysRolePermissionMapper mapper;

    private RolePermissionService service() {
        return new RolePermissionService(mapper);
    }

    @Test
    @DisplayName("库里没有该角色的记录：回退 MenuMatrix 常量，且系统管理员不含业务审核权")
    void falls_back_to_matrix_when_no_rows() {
        when(mapper.selectList(any())).thenReturn(List.of());

        RolePermissionService service = service();
        Set<String> menus = service.menusOfRole("SYS_ADMIN");
        Set<String> apis = service.apisOfRole("SYS_ADMIN");

        assertThat(menus).contains("admin", "stats");
        assertThat(apis).contains("role:manage", "user:manage");
        assertThat(apis).doesNotContain("adoption:audit", "expense:review");
    }

    @Test
    @DisplayName("库里有记录：按记录返回，菜单按侧栏顺序排列，权限码剔掉 menu: 前缀")
    void uses_stored_rows_and_keeps_menu_order() {
        SysRolePermission me = row("VOLUNTEER", "menu:me");
        SysRolePermission home = row("VOLUNTEER", "menu:home");
        SysRolePermission api = row("VOLUNTEER", "task:join");
        when(mapper.selectList(any())).thenReturn(List.of(me, home, api));

        RolePermissionService service = service();

        assertThat(service.menusOfRole("VOLUNTEER")).containsExactly("home", "me");
        assertThat(service.apisOfRole("VOLUNTEER")).containsExactly("task:join");
    }

    @Test
    @DisplayName("系统管理员两套码一起写：先删两行再按目标集合插入")
    void replace_writes_both_system_admin_codes() {
        RolePermissionService service = service();

        service.replace("SYS_ADMIN", List.of("menu:admin", "role:manage"));

        verify(mapper, times(2)).delete(any());
        ArgumentCaptor<SysRolePermission> captor = ArgumentCaptor.forClass(SysRolePermission.class);
        verify(mapper, times(4)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysRolePermission::getRoleCode)
                .containsExactlyInAnyOrder("SYS_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN", "SYSTEM_ADMIN");
    }

    @Test
    @DisplayName("多角色取并集并按侧栏顺序输出")
    void menus_for_multiple_roles_are_ordered() {
        when(mapper.selectList(any())).thenReturn(List.of());

        RolePermissionService service = service();

        assertThat(service.menusFor(Set.of("STUDENT", "VOLUNTEER")))
                .containsExactly("home", "archive", "adopt", "report", "fund", "volunteer", "content", "me");
    }

    private static SysRolePermission row(String roleCode, String permCode) {
        SysRolePermission row = new SysRolePermission();
        row.setRoleCode(roleCode);
        row.setPermCode(permCode);
        return row;
    }
}
