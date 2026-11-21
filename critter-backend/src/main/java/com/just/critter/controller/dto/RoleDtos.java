package com.just.critter.controller.dto;

import java.util.List;
import java.util.Set;

/** 角色与权限清单（GET /roles）。matrix 是当前生效的"角色 → 菜单/接口"映射，前端后台页直接展示。 */
public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleView(long id, String roleCode, String roleName, String description) {
    }

    public record PermissionView(long id, String permCode, String permName, String type, String path) {
    }

    public record RoleMatrix(String roleCode, String roleName, List<String> menus, List<String> apis) {
    }

    public record RolesResponse(List<RoleView> roles, List<PermissionView> permissions,
                                List<RoleMatrix> matrix, Set<String> allMenus) {
    }
}
