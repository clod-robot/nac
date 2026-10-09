package com.his.common.ratelimit;

import com.his.common.context.UserContext;
import com.his.common.exception.BusinessException;
import com.his.common.redis.RedisUtil;
import com.his.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private RedisUtil redisUtil;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String dimKey = resolveDimension(rateLimit.dimension());
        String method = pjp.getSignature().toShortString();
        String key = "his:rl:" + method + ":" + dimKey;
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
            // 经过网关时 X-Forwarded-For 第一个值为真实客户端 IP
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
