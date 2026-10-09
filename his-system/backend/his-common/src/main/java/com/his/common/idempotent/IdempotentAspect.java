package com.his.common.idempotent;

import com.his.common.exception.BusinessException;
import com.his.common.redis.RedisUtil;
import com.his.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    @Autowired
    private RedisUtil redisUtil;

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = buildKey(pjp, idempotent);
        String redisKey = "his:idem:" + key;
        if (!redisUtil.setIfAbsent(redisKey, idempotent.expire())) {
            log.warn("幂等拦截: {}", redisKey);
            throw new BusinessException(ResultCode.IDEMPOTENT_CONFLICT.getCode(), idempotent.message());
        }
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            // 业务异常时释放幂等锁，允许用户修正后重试
            redisUtil.delete(redisKey);
            throw e;
        }
    }

    private String buildKey(ProceedingJoinPoint pjp, Idempotent idempotent) {
        if (idempotent.key().isEmpty()) {
            String traceId = getTraceId();
            return pjp.getSignature().toLongString() + ":" + traceId;
        }
        EvaluationContext ctx = new StandardEvaluationContext();
        String[] names = nameDiscoverer.getParameterNames(((org.aspectj.lang.reflect.MethodSignature) pjp.getSignature()).getMethod());
        Object[] args = pjp.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        Expression exp = parser.parseExpression(idempotent.key());
        Object val = exp.getValue(ctx);
        return val == null ? "null" : val.toString();
    }

    private String getTraceId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String t = req.getHeader("X-Trace-Id");
                if (t != null && !t.isEmpty()) return t;
            }
        } catch (Exception ignored) {}
        return java.util.UUID.randomUUID().toString();
    }
}
