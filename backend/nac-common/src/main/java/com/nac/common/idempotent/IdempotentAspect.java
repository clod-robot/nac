package com.nac.common.idempotent;

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
 * 幂等切面：SETNX 抢占，失败抛 IDEMPOTENT_REPEAT。Redis 故障降级允许。
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    private final RedisUtil redisUtil;

    public IdempotentAspect(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = RedisKeyConstants.IDEMPOTENT_PREFIX
                + (idempotent.key().isEmpty() ? pjp.getSignature().toShortString() : idempotent.key())
                + ":" + resolveBiz();
        if (!redisUtil.setIfAbsent(key, idempotent.ttl())) {
            log.warn("幂等拦截重复提交: {}", key);
            throw new BusinessException(ResultCode.IDEMPOTENT_REPEAT);
        }
        return pjp.proceed();
    }

    private String resolveBiz() {
        Long uid = UserContext.getUserId();
        if (uid != null) return "u" + uid;
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String tid = req.getHeader("X-Trace-Id");
                if (tid != null && !tid.isEmpty()) return "t" + tid;
                return "ip" + req.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "na";
    }
}
