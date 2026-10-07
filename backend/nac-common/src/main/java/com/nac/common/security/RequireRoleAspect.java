package com.nac.common.security;

import com.nac.common.context.UserContext;
import com.nac.common.exception.BusinessException;
import com.nac.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 角色越权防护切面：校验当前用户角色。非授权抛 403。
 */
@Slf4j
@Aspect
@Component
public class RequireRoleAspect {

    @Around("@annotation(requireRole)")
    public Object around(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
        String role = UserContext.getRole();
        String[] allowed = requireRole.value();
        boolean pass;
        if (allowed == null || allowed.length == 0) {
            pass = UserContext.isAdmin();
        } else {
            pass = Arrays.asList(allowed).contains(role) || UserContext.isAdmin();
        }
        if (!pass) {
            log.warn("越权拦截: method={}, role={}, required={}",
                    pjp.getSignature().toShortString(), role, Arrays.toString(allowed));
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return pjp.proceed();
    }
}
