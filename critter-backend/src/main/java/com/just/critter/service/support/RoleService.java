package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.just.critter.controller.dto.RoleDtos;
import com.just.critter.domain.model.SysPermission;
import com.just.critter.domain.model.SysRole;
import com.just.critter.mapper.SysPermissionMapper;
import com.just.critter.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 角色与权限清单（GET /roles）：后台角色页直接渲染，不用前端再硬编码一份矩阵。 */
@Service
public class RoleService {

    private static final List<String> ROLE_ORDER =
            List.of("STUDENT", "ADOPTER", "VOLUNTEER", "ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN");

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    public RoleService(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    public RoleDtos.RolesResponse list() {
        List<SysRole> roles = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
        List<SysPermission> permissions = permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                .orderByAsc(SysPermission::getType).orderByAsc(SysPermission::getId));

        List<RoleDtos.RoleMatrix> matrix = roles.stream()
                .filter(role -> ROLE_ORDER.contains(role.getRoleCode()))
                .map(role -> {
                    // 两个码指向同一套矩阵：SYS_ADMIN（文档口径）与 SYSTEM_ADMIN（历史种子）
                    Set<String> rolesSet = MenuMatrix.isSystemAdmin(Set.of(role.getRoleCode()))
                            ? Set.of("SYSTEM_ADMIN")
                            : Set.of(role.getRoleCode());
                    List<String> menus = List.copyOf(MenuMatrix.menusFor(rolesSet));
                    List<String> apis = List.copyOf(MenuMatrix.apisFor(rolesSet));
                    return new RoleDtos.RoleMatrix(role.getRoleCode(), role.getRoleName(), menus, apis);
                })
                .toList();

        List<RoleDtos.RoleView> roleViews = roles.stream()
                .map(role -> new RoleDtos.RoleView(role.getId(), role.getRoleCode(), role.getRoleName(),
                        role.getDescription()))
                .toList();

        List<RoleDtos.PermissionView> permissionViews = permissions.stream()
                .map(item -> new RoleDtos.PermissionView(item.getId(), item.getPermCode(), item.getPermName(),
                        item.getType(), item.getPath()))
                .toList();

        Set<String> allMenus = new LinkedHashSet<>();
        MenuMatrix.MENUS.forEach(menu -> allMenus.add(menu.code()));

        return new RoleDtos.RolesResponse(roleViews, permissionViews, matrix, allMenus);
    }
}
