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

    /**
     * 权限调整入参。{@code preview=true} 时只回差异、不落库——
     * 后台页要求先看"会删掉什么"再确认，避免误操作把管理员锁在门外。
     */
    public record PermissionUpdateRequest(List<String> menus, List<String> apis, Boolean preview) {

        public List<String> menusOrEmpty() {
            return menus == null ? List.of() : menus;
        }

        public List<String> apisOrEmpty() {
            return apis == null ? List.of() : apis;
        }

        public boolean isPreview() {
            return Boolean.TRUE.equals(preview);
        }
    }

    /** 差异预览：added/removed 都按权限码给出（菜单带 menu: 前缀），前端直接渲染。 */
    public record PermissionDiff(String roleCode, String roleName,
                                 List<String> addedMenus, List<String> removedMenus,
                                 List<String> addedApis, List<String> removedApis,
                                 List<String> warnings, boolean applied) {
    }
}
