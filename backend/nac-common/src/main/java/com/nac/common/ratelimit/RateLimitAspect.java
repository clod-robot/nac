package com.nac.common.ratelimit;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.context.UserContext;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 限流切面：Redis + Lua 原子计数，超限抛 TOO_MANY_REQUESTS。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private final RedisUtil redisUtil;

    public RateLimitAspect(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = RedisKeyConstants.RATE_LIMIT_PREFIX + pjp.getSignature().toShortString() + ":"
                + resolveDimension(rateLimit.dimension());
        if (!redisUtil.tryAcquire(key, rateLimit.limit(), rateLimit.window())) {
            log.warn("限流触发: {} limit={}/{}s", key, rateLimit.limit(), rateLimit.window());
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return pjp.proceed();
    }

    private String resolveDimension(RateLimit.Dimension dim) {
        switch (dim) {
            case USER:
                Long uid = UserContext.getUserId();
                return "user:" + (uid != null ? uid : "anon");
            case GLOBAL:
                return "global";
            case IP:
            default:
                return "ip:" + getClientIp();
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
            String real = req.getHeader("X-Real-IP");
            if (real != null && !real.isEmpty()) return real;
            return req.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
