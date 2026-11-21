package com.just.critter.service.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色-菜单/接口矩阵（权限矩阵见《功能分析说明书》§2）。
 *
 * <p>为什么先写在代码里：`sys_permission` 有权限点，但库表暂未建角色-权限关联表，
 * 而基础模块的验收只要求"四种角色登录看到不同菜单"。矩阵集中在这一个常量里，
 * 后续做 PUT /roles/{id}/permissions 时把常量搬进关联表即可，调用方不用改。
 *
 * <p>一条硬约束：系统管理员不具备业务审核权（adoption:audit、expense:review 不在其权限集合内）。
 */
public final class MenuMatrix {

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

    public static Set<String> menusFor(Set<String> roles) {
        Set<String> menus = new LinkedHashSet<>();
        if (roles == null || roles.isEmpty()) {
            return menus;
        }
        if (roles.contains("STUDENT")) {
            menus.addAll(STUDENT_MENUS);
        }
        if (roles.contains("ADOPTER")) {
            menus.addAll(ADOPTER_MENUS);
        }
        if (roles.contains("VOLUNTEER")) {
            menus.addAll(VOLUNTEER_MENUS);
        }
        if (roles.contains("ANIMAL_ADMIN")) {
            menus.addAll(ANIMAL_ADMIN_MENUS);
        }
        if (isSystemAdmin(roles)) {
            menus.addAll(SYSTEM_ADMIN_MENUS);
        }
        // 按 MENUS 的定义顺序输出，前端侧栏不用再排一次
        Set<String> ordered = new LinkedHashSet<>();
        MENUS.forEach(menu -> {
            if (menus.contains(menu.code())) {
                ordered.add(menu.code());
            }
        });
        return ordered;
    }

    public static Set<String> apisFor(Set<String> roles) {
        Set<String> apis = new LinkedHashSet<>();
        if (roles == null) {
            return apis;
        }
        if (roles.contains("STUDENT")) {
            apis.addAll(STUDENT_APIS);
        }
        if (roles.contains("ADOPTER")) {
            apis.addAll(ADOPTER_APIS);
        }
        if (roles.contains("VOLUNTEER")) {
            apis.addAll(VOLUNTEER_APIS);
        }
        if (roles.contains("ANIMAL_ADMIN")) {
            apis.addAll(ANIMAL_ADMIN_APIS);
        }
        if (isSystemAdmin(roles)) {
            apis.addAll(SYSTEM_ADMIN_APIS);
        }
        return apis;
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
