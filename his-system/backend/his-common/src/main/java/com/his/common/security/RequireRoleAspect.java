package com.his.common.security;

import com.his.common.context.UserContext;
import com.his.common.exception.BusinessException;
import com.his.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 角色校验切面：标注 @RequireRole 的方法校验当前用户角色，不匹配返回 403。
 * 与前端 v-permission 形成纵深防御。
 */
@Slf4j
@Aspect
@Component
public class RequireRoleAspect {

    @Around("@annotation(requireRole)")
    public Object around(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
        String role = UserContext.getRole();
        if (role == null || !Arrays.asList(requireRole.value()).contains(role)) {
            log.warn("越权访问拦截: method={} required={} actual={}",
                    pjp.getSignature().toShortString(), Arrays.toString(requireRole.value()), role);
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return pjp.proceed();
    }
}
