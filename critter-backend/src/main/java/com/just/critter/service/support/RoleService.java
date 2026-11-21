package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.just.critter.controller.dto.RoleDtos;
import com.just.critter.domain.model.SysPermission;
import com.just.critter.domain.model.SysRole;
import com.just.critter.mapper.SysPermissionMapper;
import com.just.critter.mapper.SysRoleMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.exception.FieldErrorItem;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色与权限清单（GET /roles）与在线配置（PUT /roles/{id}/permissions）。
 *
 * <p>矩阵的权威来源是 {@code sys_role_permission}（V7），页面展示的就是真正生效的鉴权依据，
 * 不会出现"页面说有权限、接口却 403"。两套系统管理员码（SYS_ADMIN / SYSTEM_ADMIN）成对读写。</p>
 *
 * <p>防自锁三条：系统管理员必须保留 {@code menu:admin} 与 {@code role:manage}；
 * 调用者若正持有目标角色，不允许把自己的 {@code role:manage} 摘掉；
 * 权限码必须存在于 {@code sys_permission}，避免手滑写出拼错的码让整条链路失效。</p>
 */
@Service
public class RoleService {

    private static final List<String> ROLE_ORDER =
            List.of("STUDENT", "ADOPTER", "VOLUNTEER", "ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN");

    /** 系统管理员必须保留的两项：后台菜单与角色配置接口。 */
    private static final String REQUIRED_MENU = "admin";
    private static final String REQUIRED_API = "role:manage";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final RolePermissionService rolePermissionService;
    private final AuditLogService auditLog;

    public RoleService(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                       RolePermissionService rolePermissionService, AuditLogService auditLog) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionService = rolePermissionService;
        this.auditLog = auditLog;
    }

    public RoleDtos.RolesResponse list() {
        List<SysRole> roles = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
        List<SysPermission> permissions = permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                .orderByAsc(SysPermission::getType).orderByAsc(SysPermission::getId));

        List<RoleDtos.RoleMatrix> matrix = roles.stream()
                .filter(role -> ROLE_ORDER.contains(role.getRoleCode()))
                .map(role -> new RoleDtos.RoleMatrix(role.getRoleCode(), role.getRoleName(),
                        List.copyOf(rolePermissionService.menusOfRole(role.getRoleCode())),
                        List.copyOf(rolePermissionService.apisOfRole(role.getRoleCode()))))
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

    /**
     * 在线配置角色权限。{@code preview=true} 只算差异不写库，前端确认后再调一次落库。
     */
    @Transactional
    public RoleDtos.PermissionDiff updatePermissions(long roleId, RoleDtos.PermissionUpdateRequest request) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        String roleCode = role.getRoleCode();

        List<String> menus = normalizeMenus(request.menusOrEmpty());
        List<String> apis = normalizeApis(request.apisOrEmpty());
        validateMenus(menus);
        validateApis(apis);
        guardSelfLock(roleCode, menus, apis);

        List<String> current = rolePermissionService.storedPerms(roleCode);
        Set<String> currentMenus = new LinkedHashSet<>();
        Set<String> currentApis = new LinkedHashSet<>();
        split(current, currentMenus, currentApis);

        Set<String> targetMenus = new LinkedHashSet<>(menus);
        Set<String> targetApis = new LinkedHashSet<>(apis);

        List<String> addedMenus = targetMenus.stream().filter(item -> !currentMenus.contains(item)).toList();
        List<String> removedMenus = currentMenus.stream().filter(item -> !targetMenus.contains(item)).toList();
        List<String> addedApis = targetApis.stream().filter(item -> !currentApis.contains(item)).toList();
        List<String> removedApis = currentApis.stream().filter(item -> !targetApis.contains(item)).toList();

        List<String> warnings = new ArrayList<>();
        if (isSystemAdminCode(roleCode)) {
            warnings.add("系统管理员两套角色码（SYS_ADMIN / SYSTEM_ADMIN）已同步，改动对两类账号同时生效");
            if (targetApis.contains("adoption:audit") || targetApis.contains("expense:review")) {
                warnings.add("注意：系统管理员按分工不应具备业务审核权（adoption:audit / expense:review）");
            }
        }
        if (addedMenus.isEmpty() && removedMenus.isEmpty() && addedApis.isEmpty() && removedApis.isEmpty()) {
            warnings.add("与当前配置相比没有任何变化");
        }

        boolean toApply = !request.isPreview();
        if (toApply) {
            List<String> merged = new ArrayList<>();
            merged.addAll(targetMenus);
            merged.addAll(targetApis);
            rolePermissionService.replace(roleCode, merged);
            auditLog.success("ROLE_PERMISSION_UPDATE", "sys_role", String.valueOf(roleId),
                    Map.of("menus", List.copyOf(currentMenus), "apis", List.copyOf(currentApis)),
                    Map.of("menus", List.copyOf(targetMenus), "apis", List.copyOf(targetApis)));
        }

        return new RoleDtos.PermissionDiff(roleCode, role.getRoleName(),
                addedMenus, removedMenus, addedApis, removedApis, warnings, toApply);
    }

    // ---------------- 内部 ----------------

    /** 前端可能传 home 也可能传 menu:home，统一成带前缀的权限码。 */
    private List<String> normalizeMenus(List<String> raw) {
        List<String> menus = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String code = item.trim();
            String menuCode = MenuMatrix.menuCodeOf(code) == null ? code : MenuMatrix.menuCodeOf(code);
            if (!menus.contains(MenuMatrix.menuPerm(menuCode))) {
                menus.add(MenuMatrix.menuPerm(menuCode));
            }
        }
        return menus;
    }

    private List<String> normalizeApis(List<String> raw) {
        List<String> apis = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String code = item.trim();
            if (!apis.contains(code)) {
                apis.add(code);
            }
        }
        return apis;
    }

    private void validateMenus(List<String> menus) {
        List<FieldErrorItem> errors = new ArrayList<>();
        List<String> known = MenuMatrix.MENUS.stream().map(menu -> MenuMatrix.menuPerm(menu.code())).toList();
        for (String menu : menus) {
            if (!known.contains(menu)) {
                errors.add(new FieldErrorItem("menus", "未知菜单：" + menu));
            }
        }
        throwIfAny(errors);
    }

    private void validateApis(List<String> apis) {
        if (apis.isEmpty()) {
            return;
        }
        Set<String> known = new LinkedHashSet<>(permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                        .eq(SysPermission::getType, "API"))
                .stream().map(SysPermission::getPermCode).toList());
        List<FieldErrorItem> errors = new ArrayList<>();
        for (String api : apis) {
            if (!known.contains(api)) {
                errors.add(new FieldErrorItem("apis", "未知接口权限：" + api));
            }
        }
        throwIfAny(errors);
    }

    /** 防自锁：不许把系统管理员的后台入口与角色配置权摘掉；也不许摘掉自己的 role:manage。 */
    private void guardSelfLock(String roleCode, List<String> menus, List<String> apis) {
        List<FieldErrorItem> errors = new ArrayList<>();
        if (isSystemAdminCode(roleCode)) {
            if (!menus.contains(MenuMatrix.menuPerm(REQUIRED_MENU))) {
                errors.add(new FieldErrorItem("menus", "系统管理员必须保留后台管理菜单（menu:admin）"));
            }
            if (!apis.contains(REQUIRED_API)) {
                errors.add(new FieldErrorItem("apis", "系统管理员必须保留角色配置权限（role:manage）"));
            }
        }
        CurrentUser caller = AuthContext.get();
        if (caller != null && caller.hasRole(roleCode) && !apis.contains(REQUIRED_API)) {
            errors.add(new FieldErrorItem("apis", "不能摘掉自己当前角色（" + roleCode + "）的 role:manage，否则将无法再进入本页"));
        }
        throwIfAny(errors);
    }

    private static void throwIfAny(List<FieldErrorItem> errors) {
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "权限配置校验失败", errors);
        }
    }

    private static void split(List<String> codes, Set<String> menus, Set<String> apis) {
        for (String code : codes) {
            if (MenuMatrix.menuCodeOf(code) == null) {
                apis.add(code);
            } else {
                menus.add(code);
            }
        }
    }

    private static boolean isSystemAdminCode(String roleCode) {
        return "SYS_ADMIN".equals(roleCode) || "SYSTEM_ADMIN".equals(roleCode);
    }
}
