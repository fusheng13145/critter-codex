package com.just.critter.support.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级角色限制：值之间是"任一满足"。
 * 系统管理员不具备业务审核权，因此审核类接口的 roles 里不允许出现 SYSTEM_ADMIN。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRoles {

    String[] value();
}
