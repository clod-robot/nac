package com.nac.common.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具：所有方法对 Redis 故障降级（返回安全默认值），避免中间件故障拖垮业务。
 */
@Slf4j
@Component
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    /** 限流 Lua：单事务内 INCR + 首次 EXPIRE，超过 limit 返回 0 */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end " +
            "if current > tonumber(ARGV[1]) then return 0 end " +
            "return 1", Long.class);

    public RedisUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 限流器：window 秒内最多 limit 次。Redis 故障降级放行。 */
    public boolean tryAcquire(String key, int limit, int window) {
        try {
            Long r = redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(key),
                    String.valueOf(limit), String.valueOf(window));
            return r != null && r == 1L;
        } catch (Exception e) {
            log.warn("Redis 限流降级放行: {}", key, e);
            return true;
        }
    }

    /** SETNX 幂等：成功返回 true。Redis 故障降级允许。 */
    public boolean setIfAbsent(String key, int ttlSeconds) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttlSeconds, TimeUnit.SECONDS);
            return ok == null || ok;
        } catch (Exception e) {
            log.warn("Redis 幂等降级允许: {}", key, e);
            return true;
        }
    }

    public void set(String key, String value, int ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set 失败: {}", key, e);
        }
    }

    /** 持久化写入（无过期），用于共享密钥等需长期保存的配置。 */
    public void set(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.warn("Redis set 失败: {}", key, e);
        }
    }

    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis get 失败: {}", key, e);
            return null;
        }
    }

    public boolean hasKey(String key) {
        try {
            Boolean b = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(b);
        } catch (Exception e) {
            log.warn("Redis hasKey 失败: {}", key, e);
            return false;
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete 失败: {}", key, e);
        }
    }

    public boolean expire(String key, int ttlSeconds) {
        try {
            Boolean b = redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(b);
        } catch (Exception e) {
            log.warn("Redis expire 失败: {}", key, e);
            return false;
        }
    }

    public long incr(String key, int ttlSeconds) {
        try {
            Long v = redisTemplate.opsForValue().increment(key);
            if (v != null && v == 1L) {
                redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            }
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.warn("Redis incr 失败: {}", key, e);
            return 0;
        }
    }
}
