package com.just.critter.support.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内实现：单机演示足够，重启即清空（令牌全部失效、验证码需重发）。
 * 过期采用惰性判断 + 写入时顺带清理，避免引入额外线程。
 */
@Component
@ConditionalOnProperty(prefix = "critter.cache", name = "mode", havingValue = "memory", matchIfMissing = true)
public class MemoryKeyValueStore implements KeyValueStore {

    private static final int CLEAN_THRESHOLD = 512;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void set(String key, String value, Duration ttl) {
        cleanupIfNeeded();
        store.put(key, new Entry(value, expiresAt(ttl), null));
    }

    @Override
    public Optional<String> get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.ofNullable(entry.value);
    }

    @Override
    public long increment(String key, Duration ttl) {
        cleanupIfNeeded();
        Entry entry = store.compute(key, (k, current) -> {
            if (current == null || current.isExpired()) {
                return new Entry(null, expiresAt(ttl), new AtomicLong(1));
            }
            current.counter.incrementAndGet();
            return current;
        });
        return entry.counter.get();
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return get(key).isPresent();
    }

    @Override
    public String mode() {
        return "memory";
    }

    private static long expiresAt(Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative()
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + ttl.toMillis();
    }

    private void cleanupIfNeeded() {
        if (store.size() < CLEAN_THRESHOLD) {
            return;
        }
        store.entrySet().removeIf(item -> item.getValue().isExpired());
    }

    private static final class Entry {

        private final String value;
        private final long expireAt;
        private final AtomicLong counter;

        private Entry(String value, long expireAt, AtomicLong counter) {
            this.value = value;
            this.expireAt = expireAt;
            this.counter = counter;
        }

        private boolean isExpired() {
            return expireAt != Long.MAX_VALUE && System.currentTimeMillis() > expireAt;
        }
    }
}
