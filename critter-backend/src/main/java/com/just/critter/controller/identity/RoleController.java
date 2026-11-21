package com.just.critter.controller.identity;

import com.just.critter.controller.dto.RoleDtos;
import com.just.critter.service.support.RoleService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色与权限清单（后台角色页）。菜单与接口矩阵由服务端给出，前端不另存一份。
 * 在线配置走 PUT /roles/{id}/permissions：先 preview 看差异，确认后再落库。
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequireRoles({"SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<RoleDtos.RolesResponse> list() {
        return Result.ok(roleService.list());
    }

    @PutMapping("/{id}/permissions")
    @RequireRoles({"SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<RoleDtos.PermissionDiff> updatePermissions(
            @PathVariable long id,
            @RequestBody RoleDtos.PermissionUpdateRequest request) {
        return Result.ok(roleService.updatePermissions(id, request));
    }
}
