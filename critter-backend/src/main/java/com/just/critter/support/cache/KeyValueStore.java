package com.just.critter.support.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * 键值存储抽象：令牌黑名单、登录失败计数、注册验证码都放这里。
 * 本机无 Redis 时用进程内实现（{@code critter.cache.mode=memory}），
 * 装了 Redis 改成 {@code redis} 即切到真实实现，业务代码零改动。
 */
public interface KeyValueStore {

    void set(String key, String value, Duration ttl);

    Optional<String> get(String key);

    /** 原子自增；首次调用时按 ttl 设置过期时间，返回自增后的值。 */
    long increment(String key, Duration ttl);

    void delete(String key);

    boolean exists(String key);

    /** 实现名，启动日志与 /ping 回显，便于现场确认用的是内存还是 Redis。 */
    String mode();
}
