package com.nac.common.redis;

import java.util.function.Supplier;

/**
 * 配置项缓存一致性工具（cache-aside）：
 * - 读：Redis → 未命中查库 → 回填缓存 → 仍空用默认值；保证 Redis 与 DB 一致。
 * - 写：先落库（持久化真值），再刷新 Redis；两者始终同步。
 */
public final class ConfigCache {

    private ConfigCache() {}

    /** 读：缓存优先，未命中查库回填，最终为空返回 fallback。 */
    public static String get(RedisUtil redis, String key, Supplier<String> dbLoad, String fallback) {
        String v = redis.get(key);
        if (v == null) {
            v = dbLoad.get();
            if (v != null && !v.isEmpty()) {
                redis.set(key, v);
            }
        }
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    /** 写：先持久化到 DB，再刷新缓存，保证二者一致。 */
    public static void put(RedisUtil redis, String key, String value, Runnable dbWrite) {
        dbWrite.run();
        redis.set(key, value);
    }
}
