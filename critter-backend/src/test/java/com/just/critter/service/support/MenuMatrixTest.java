package com.just.critter.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色-菜单矩阵（《功能分析说明书》§2、成员任务 §1.5）：
 * 学生看不到后台与统计；系统管理员有后台但没有业务审核权。
 */
class MenuMatrixTest {

    @Test
    @DisplayName("学生菜单不含后台与统计，也不含医疗/志愿")
    void student_menus() {
        Set<String> menus = MenuMatrix.menusFor(Set.of("STUDENT"));

        assertThat(menus).containsExactly("home", "archive", "adopt", "report", "fund", "content", "me");
        assertThat(menus).doesNotContain("admin", "stats", "medical", "volunteer");
    }

    @Test
    @DisplayName("系统管理员有后台，但接口权限里没有 adoption:audit / expense:review")
    void system_admin_has_no_business_audit() {
        Set<String> menus = MenuMatrix.menusFor(Set.of("SYSTEM_ADMIN"));
        Set<String> apis = MenuMatrix.apisFor(Set.of("SYSTEM_ADMIN"));

        assertThat(menus).contains("admin", "stats");
        assertThat(apis).contains("dict:write", "log:read", "user:manage");
        assertThat(apis).doesNotContain("adoption:audit", "expense:review", "expense:write");
    }

    @Test
    @DisplayName("动物管理员有业务审核权，没有后台权限")
    void animal_admin_has_business_audit() {
        Set<String> menus = MenuMatrix.menusFor(Set.of("ANIMAL_ADMIN"));
        Set<String> apis = MenuMatrix.apisFor(Set.of("ANIMAL_ADMIN"));

        assertThat(menus).contains("medical", "stats").doesNotContain("admin");
        assertThat(apis).contains("adoption:audit", "expense:review", "report:accept")
                .doesNotContain("dict:write", "user:manage");
    }

    @Test
    @DisplayName("叠加身份取并集：学生 + 志愿者多出任务大厅")
    void roles_are_additive() {
        Set<String> menus = MenuMatrix.menusFor(Set.of("STUDENT", "VOLUNTEER"));
        Set<String> apis = MenuMatrix.apisFor(Set.of("STUDENT", "VOLUNTEER"));

        assertThat(menus).contains("volunteer", "home");
        assertThat(apis).contains("task:join", "report:write");
    }

    @Test
    @DisplayName("系统管理员两个码等价：SYS_ADMIN（文档）与 SYSTEM_ADMIN（历史种子）")
    void system_admin_alias() {
        Set<String> legacy = MenuMatrix.menusFor(Set.of("SYSTEM_ADMIN"));
        Set<String> documented = MenuMatrix.menusFor(Set.of("SYS_ADMIN"));

        assertThat(documented).isEqualTo(legacy).contains("admin");
        assertThat(MenuMatrix.roleLabel("SYS_ADMIN")).isEqualTo("系统管理员");
        assertThat(MenuMatrix.roleLabel("SYSTEM_ADMIN")).isEqualTo("系统管理员");
    }

    @Test
    @DisplayName("菜单顺序与原型侧栏一致，前端不再排序")
    void menu_order_matches_prototype() {
        Set<String> menus = MenuMatrix.menusFor(Set.of("ANIMAL_ADMIN"));

        assertThat(menus).containsExactly("home", "archive", "adopt", "report", "fund",
                "medical", "volunteer", "content", "stats", "me");
    }
}
