package com.nac.auth.service;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.redis.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 登录失败锁定：同 IP 5次/5min 锁 15min；同账号 10次/5min 锁 30min。
 * Redis 故障降级为不锁定（允许认证），避免中间件故障导致全员无法登录。
 */
@Slf4j
@Service
public class LoginAttemptService {

    private static final int IP_MAX_FAIL = 5;
    private static final int USER_MAX_FAIL = 10;
    private static final int FAIL_WINDOW = 300;     // 5min
    private static final int IP_LOCK_TTL = 900;    // 15min
    private static final int USER_LOCK_TTL = 1800;  // 30min

    private final RedisUtil redisUtil;

    public LoginAttemptService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    public boolean isIpLocked(String ip) {
        return redisUtil.hasKey(RedisKeyConstants.LOGIN_LOCK_IP_PREFIX + ip);
    }

    public boolean isUserLocked(String username) {
        return redisUtil.hasKey(RedisKeyConstants.LOGIN_LOCK_USER_PREFIX + username);
    }

    public void recordFail(String ip, String username) {
        long ipCount = redisUtil.incr(RedisKeyConstants.LOGIN_FAIL_IP_PREFIX + ip, FAIL_WINDOW);
        if (ipCount >= IP_MAX_FAIL) {
            redisUtil.set(RedisKeyConstants.LOGIN_LOCK_IP_PREFIX + ip, "1", IP_LOCK_TTL);
            log.warn("IP 登录失败锁定: ip={}", ip);
        }
        long userCount = redisUtil.incr(RedisKeyConstants.LOGIN_FAIL_USER_PREFIX + username, FAIL_WINDOW);
        if (userCount >= USER_MAX_FAIL) {
            redisUtil.set(RedisKeyConstants.LOGIN_LOCK_USER_PREFIX + username, "1", USER_LOCK_TTL);
            log.warn("账号登录失败锁定: user={}", username);
        }
    }

    public void clear(String ip, String username) {
        redisUtil.delete(RedisKeyConstants.LOGIN_FAIL_IP_PREFIX + ip);
        redisUtil.delete(RedisKeyConstants.LOGIN_FAIL_USER_PREFIX + username);
    }
}
