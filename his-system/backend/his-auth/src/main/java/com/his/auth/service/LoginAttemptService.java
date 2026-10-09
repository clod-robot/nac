package com.his.auth.service;

import com.his.common.constant.RedisKeyConstants;
import com.his.common.redis.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 登录防爆破：
 * - 同 IP 5 分钟内失败 5 次锁定 15 分钟
 * - 同账号 5 分钟内失败 10 次锁定 30 分钟
 * Redis 不可用时降级放行（避免中间件故障导致全员无法登录）。
 */
@Service
public class LoginAttemptService {

    @Autowired
    private RedisUtil redisUtil;

    private static final int IP_MAX_FAIL = 5;
    private static final int USER_MAX_FAIL = 10;
    private static final int WINDOW_SEC = 300;
    private static final int IP_LOCK_SEC = 900;
    private static final int USER_LOCK_SEC = 1800;

    public boolean isIpLocked(String ip) {
        try {
            return Boolean.TRUE.equals(redisUtil.hasKey(RedisKeyConstants.LOGIN_LOCK_IP_PREFIX + ip));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUserLocked(String username) {
        try {
            return Boolean.TRUE.equals(redisUtil.hasKey(RedisKeyConstants.LOGIN_LOCK_USER_PREFIX + username));
        } catch (Exception e) {
            return false;
        }
    }

    public void recordFail(String ip, String username) {
        try {
            String ipKey = RedisKeyConstants.LOGIN_FAIL_IP_PREFIX + ip;
            Long ipCount = redisUtil.increment(ipKey);
            if (ipCount != null && ipCount == 1L) redisUtil.expire(ipKey, WINDOW_SEC);
            if (ipCount != null && ipCount >= IP_MAX_FAIL) {
                redisUtil.set(RedisKeyConstants.LOGIN_LOCK_IP_PREFIX + ip, "1", IP_LOCK_SEC);
            }
            String userKey = RedisKeyConstants.LOGIN_FAIL_USER_PREFIX + username;
            Long userCount = redisUtil.increment(userKey);
            if (userCount != null && userCount == 1L) redisUtil.expire(userKey, WINDOW_SEC);
            if (userCount != null && userCount >= USER_MAX_FAIL) {
                redisUtil.set(RedisKeyConstants.LOGIN_LOCK_USER_PREFIX + username, "1", USER_LOCK_SEC);
            }
        } catch (Exception ignored) {}
    }

    public void recordSuccess(String ip, String username) {
        try {
            redisUtil.delete(RedisKeyConstants.LOGIN_FAIL_IP_PREFIX + ip);
            redisUtil.delete(RedisKeyConstants.LOGIN_FAIL_USER_PREFIX + username);
        } catch (Exception ignored) {}
    }
}
