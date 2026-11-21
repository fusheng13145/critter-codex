package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-权限关联（V7 新建）。
 *
 * <p>权限码沿用 {@code sys_permission} 口径：菜单是 {@code menu:home} 这类，
 * 接口是 {@code role:manage} 这类。表里只存码，不存外键——权限点由种子维护，
 * 删除某个权限点不该连带删掉历史分配记录。</p>
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String roleCode;

    private String permCode;

    private LocalDateTime createdAt;
}
