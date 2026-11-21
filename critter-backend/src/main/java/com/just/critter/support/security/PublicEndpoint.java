package com.just.critter.support.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记允许匿名访问的端点。必须落在具体 Controller 方法上，
 * 鉴权拦截器在解析令牌之前放行，避免把整条路径（含写操作）误设为匿名。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
