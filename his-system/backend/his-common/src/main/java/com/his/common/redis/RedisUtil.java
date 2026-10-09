package com.his.common.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作封装。所有 KEYS 操作禁用，大 key 遍历使用 SCAN。
 */
@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local current = redis.call('INCR', key)\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', key, window)\n" +
            "end\n" +
            "if current > limit then\n" +
            "    return 0\n" +
            "end\n" +
            "return 1", Long.class);

    public void set(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Boolean expire(String key, long ttlSeconds) {
        return redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 原子限流：单窗口内超过 limit 次返回 false。使用 Lua 保证 INCR+EXPIRE 原子性，
     * 避免 Redis 重启后 key 永久残留（参考 NAC 项目 lessons learned）。
     */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long r = redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(key),
                String.valueOf(limit), String.valueOf(windowSeconds));
        return r != null && r == 1L;
    }

    /**
     * 幂等占位：SETNX + TTL，成功返回 true 表示首次请求
     */
    public boolean setIfAbsent(String key, long ttlSeconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }
}
