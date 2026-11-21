package com.just.critter.support.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存键值实现（无 Redis 时的退路）：计数自增、过期清理、删除。
 * Redis 实现与本实现共用 KeyValueStore 契约，切换只需改配置。
 */
class MemoryKeyValueStoreTest {

    private final MemoryKeyValueStore store = new MemoryKeyValueStore();

    @Test
    @DisplayName("自增从 1 开始并累加")
    void increment_counts() {
        Duration ttl = Duration.ofMinutes(10);

        assertThat(store.increment("login:fail:acct:a", ttl)).isEqualTo(1);
        assertThat(store.increment("login:fail:acct:a", ttl)).isEqualTo(2);
        assertThat(store.increment("login:fail:acct:a", ttl)).isEqualTo(3);
    }

    @Test
    @DisplayName("过期后取不到，计数重新开始")
    void ttl_expires() throws InterruptedException {
        store.set("code:1", "123456", Duration.ofMillis(80));
        assertThat(store.get("code:1")).contains("123456");

        Thread.sleep(120);

        assertThat(store.get("code:1")).isEmpty();
        assertThat(store.exists("code:1")).isFalse();
    }

    @Test
    @DisplayName("删除后不可见")
    void delete_removes() {
        store.set("black:jti", "1", Duration.ofMinutes(5));
        store.delete("black:jti");

        assertThat(store.exists("black:jti")).isFalse();
    }

    @Test
    @DisplayName("实现名回显为 memory，便于现场确认未误连 Redis")
    void mode_is_reported() {
        assertThat(store.mode()).isEqualTo("memory");
    }
}
