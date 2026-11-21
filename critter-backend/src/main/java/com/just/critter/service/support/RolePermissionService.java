package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.just.critter.domain.model.SysRolePermission;
import com.just.critter.mapper.SysRolePermissionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 角色权限的读写口子（V7 起以 {@code sys_role_permission} 为权威来源）。
 *
 * <p>读取规则：按角色码取库里的权限码；某个角色一条记录都没有时，
 * 回退到 {@link MenuMatrix} 的常量，避免误删数据把整类账号锁在门外。
 * 因此"想清空某角色的全部菜单"不生效——至少保留一个菜单再改其余项。</p>
 *
 * <p>系统管理员有两套等价角色码（SYS_ADMIN / SYSTEM_ADMIN），读写都按一对处理，
 * 否则改了文档口径的码、历史种子的账号却不受影响，现场很难解释。</p>
 */
@Service
public class RolePermissionService {

    private final SysRolePermissionMapper mapper;

    public RolePermissionService(SysRolePermissionMapper mapper) {
        this.mapper = mapper;
    }

    /** 多个角色的菜单并集（登录与 /users/me 用），按侧栏顺序输出。 */
    public Set<String> menusFor(Set<String> roles) {
        Set<String> menus = new LinkedHashSet<>();
        if (roles == null) {
            return menus;
        }
        roles.forEach(role -> menus.addAll(menusOfRole(role)));
        return MenuMatrix.orderMenus(menus);
    }

    /** 多个角色的接口权限并集。 */
    public Set<String> apisFor(Set<String> roles) {
        Set<String> apis = new LinkedHashSet<>();
        if (roles == null) {
            return apis;
        }
        roles.forEach(role -> apis.addAll(apisOfRole(role)));
        return apis;
    }

    /** 单角色菜单；库里无记录时回退常量。 */
    public Set<String> menusOfRole(String roleCode) {
        List<String> stored = storedPerms(roleCode);
        if (stored.isEmpty()) {
            return MenuMatrix.menusOf(roleCode);
        }
        Set<String> menus = new LinkedHashSet<>();
        stored.stream().map(MenuMatrix::menuCodeOf).filter(Objects::nonNull).forEach(menus::add);
        return MenuMatrix.orderMenus(menus);
    }

    /** 单角色接口权限；库里无记录时回退常量。 */
    public Set<String> apisOfRole(String roleCode) {
        List<String> stored = storedPerms(roleCode);
        if (stored.isEmpty()) {
            return MenuMatrix.apisOf(roleCode);
        }
        Set<String> apis = new LinkedHashSet<>();
        stored.stream().filter(code -> MenuMatrix.menuCodeOf(code) == null).forEach(apis::add);
        return apis;
    }

    /** 库里实存的权限码（含 menu: 前缀），供差异预览对比。 */
    public List<String> storedPerms(String roleCode) {
        List<String> codes = new ArrayList<>();
        for (String code : roleCodesOf(roleCode)) {
            mapper.selectList(Wrappers.<SysRolePermission>lambdaQuery()
                            .eq(SysRolePermission::getRoleCode, code))
                    .forEach(row -> codes.add(row.getPermCode()));
        }
        return codes;
    }

    /** 覆写某角色的权限集合；系统管理员两套码一起写。 */
    public void replace(String roleCode, Collection<String> permCodes) {
        for (String code : roleCodesOf(roleCode)) {
            mapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                    .eq(SysRolePermission::getRoleCode, code));
            LinkedHashSet<String> distinct = new LinkedHashSet<>(permCodes);
            for (String perm : distinct) {
                SysRolePermission row = new SysRolePermission();
                row.setRoleCode(code);
                row.setPermCode(perm);
                mapper.insert(row);
            }
        }
    }

    /** 系统管理员的两个码视为同一角色，读写都要成对。 */
    public List<String> roleCodesOf(String roleCode) {
        return MenuMatrix.isSystemAdmin(Set.of(roleCode == null ? "" : roleCode))
                ? List.of("SYS_ADMIN", "SYSTEM_ADMIN")
                : List.of(roleCode);
    }
}
