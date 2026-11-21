package com.just.critter.service.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色-菜单/接口矩阵（权限矩阵见《功能分析说明书》§2）。
 *
 * <p>V7 起矩阵的权威来源是 {@code sys_role_permission} 表，
 * {@link RolePermissionService} 读库后再套这里的顺序；本类的常量降级为两件事：
 * 首次种子（V7 的 SQL 就是照这份常量写的）与"库里查不到该角色时"的兜底，
 * 避免一条 DELETE 把管理员锁在门外。</p>
 *
 * <p>一条硬约束：系统管理员不具备业务审核权（adoption:audit、expense:review 不在其权限集合内）。</p>
 */
public final class MenuMatrix {

    /** 菜单权限码前缀，与 sys_permission 的 MENU 类型一致。 */
    public static final String MENU_PERM_PREFIX = "menu:";

    public record MenuDef(String code, String title, String path, String owner) {
    }

    /** 侧栏顺序与原型一致；owner 标明该页归属，占位页据此提示"由谁实现"。 */
    public static final List<MenuDef> MENUS = List.of(
            new MenuDef("home", "工作台", "/", "A"),
            new MenuDef("archive", "生物档案", "/animals", "B"),
            new MenuDef("adopt", "认养专区", "/adopt", "C"),
            new MenuDef("report", "救助上报", "/report", "B"),
            new MenuDef("fund", "资金透明", "/fund", "B"),
            new MenuDef("medical", "医疗协作", "/medical", "C"),
            new MenuDef("volunteer", "志愿任务", "/tasks", "C"),
            new MenuDef("content", "消息中心", "/messages", "C"),
            new MenuDef("stats", "数据统计", "/stats", "A"),
            new MenuDef("admin", "后台管理", "/admin", "A"),
            new MenuDef("me", "个人中心", "/me", "A"));

    /** 菜单码 → 权限码。库里存的是 menu:home 这种形式。 */
    public static String menuPerm(String menuCode) {
        return MENU_PERM_PREFIX + menuCode;
    }

    /** 权限码 → 菜单码；不是菜单权限时返回 null。 */
    public static String menuCodeOf(String permCode) {
        return permCode != null && permCode.startsWith(MENU_PERM_PREFIX)
                ? permCode.substring(MENU_PERM_PREFIX.length())
                : null;
    }

    private static final Set<String> STUDENT_MENUS =
            Set.of("home", "archive", "adopt", "report", "fund", "content", "me");
    private static final Set<String> ADOPTER_MENUS =
            Set.of("home", "archive", "adopt", "report", "fund", "content", "me");
    private static final Set<String> VOLUNTEER_MENUS =
            Set.of("home", "archive", "adopt", "report", "fund", "volunteer", "content", "me");
    private static final Set<String> ANIMAL_ADMIN_MENUS =
            Set.of("home", "archive", "adopt", "report", "fund", "medical", "volunteer", "content", "stats", "me");
    private static final Set<String> SYSTEM_ADMIN_MENUS =
            Set.of("home", "archive", "fund", "content", "stats", "admin", "me");

    private static final Set<String> STUDENT_APIS =
            Set.of("animal:read", "report:write", "adoption:apply", "donation:write", "file:upload", "profile:write");
    private static final Set<String> ADOPTER_APIS =
            Set.of("animal:read", "report:write", "adoption:apply", "donation:write", "file:upload", "profile:write");
    private static final Set<String> VOLUNTEER_APIS =
            Set.of("animal:read", "report:write", "adoption:apply", "donation:write", "file:upload", "profile:write",
                    "task:join");
    private static final Set<String> ANIMAL_ADMIN_APIS =
            Set.of("animal:read", "animal:write", "report:read", "report:accept", "adoption:audit",
                    "donation:verify", "expense:write", "expense:review", "tnr:write", "task:publish",
                    "file:upload", "file:audit", "dict:read", "log:read-self", "stats:read");
    private static final Set<String> SYSTEM_ADMIN_APIS =
            Set.of("animal:read", "dict:read", "dict:write", "user:manage", "role:manage",
                    "log:read", "stats:read", "file:upload");

    private MenuMatrix() {
    }

    /** 单个角色的菜单集合（常量口径，供 DB 兜底与 V7 种子对齐）。 */
    public static Set<String> menusOf(String roleCode) {
        Set<String> menus = switch (roleCode == null ? "" : roleCode) {
            case "STUDENT" -> STUDENT_MENUS;
            case "ADOPTER" -> ADOPTER_MENUS;
            case "VOLUNTEER" -> VOLUNTEER_MENUS;
            case "ANIMAL_ADMIN" -> ANIMAL_ADMIN_MENUS;
            case "SYS_ADMIN", "SYSTEM_ADMIN" -> SYSTEM_ADMIN_MENUS;
            default -> Set.of();
        };
        return orderMenus(menus);
    }

    /** 单个角色的接口权限集合（常量口径）。 */
    public static Set<String> apisOf(String roleCode) {
        return switch (roleCode == null ? "" : roleCode) {
            case "STUDENT" -> STUDENT_APIS;
            case "ADOPTER" -> ADOPTER_APIS;
            case "VOLUNTEER" -> VOLUNTEER_APIS;
            case "ANIMAL_ADMIN" -> ANIMAL_ADMIN_APIS;
            case "SYS_ADMIN", "SYSTEM_ADMIN" -> SYSTEM_ADMIN_APIS;
            default -> Set.of();
        };
    }

    public static Set<String> menusFor(Set<String> roles) {
        Set<String> menus = new LinkedHashSet<>();
        if (roles == null || roles.isEmpty()) {
            return menus;
        }
        roles.forEach(role -> menus.addAll(menusOf(role)));
        return orderMenus(menus);
    }

    public static Set<String> apisFor(Set<String> roles) {
        Set<String> apis = new LinkedHashSet<>();
        if (roles == null) {
            return apis;
        }
        roles.forEach(role -> apis.addAll(apisOf(role)));
        return apis;
    }

    /** 按 MENUS 的定义顺序输出，前端侧栏不用再排一次。 */
    public static Set<String> orderMenus(Set<String> menus) {
        Set<String> ordered = new LinkedHashSet<>();
        MENUS.forEach(menu -> {
            if (menus.contains(menu.code())) {
                ordered.add(menu.code());
            }
        });
        return ordered;
    }

    /**
     * 系统管理员有两个码：接口文档定的是 SYS_ADMIN，库里历史种子用的是 SYSTEM_ADMIN。
     * 两种都认，避免"换个账号登录菜单就空了"。
     */
    public static boolean isSystemAdmin(Set<String> roles) {
        return roles != null && (roles.contains("SYS_ADMIN") || roles.contains("SYSTEM_ADMIN"));
    }

    /** 角色中文名，用于菜单/日志展示，避免前端再维护一份映射。 */
    public static String roleLabel(String roleCode) {
        if (roleCode == null) {
            return "游客";
        }
        return switch (roleCode) {
            case "STUDENT" -> "注册学生";
            case "ADOPTER" -> "认养人";
            case "VOLUNTEER" -> "志愿者";
            case "ANIMAL_ADMIN" -> "动物管理员";
            case "SYS_ADMIN", "SYSTEM_ADMIN" -> "系统管理员";
            default -> "游客";
        };
    }
}
