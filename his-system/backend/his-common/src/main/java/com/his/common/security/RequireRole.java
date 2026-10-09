package com.his.common.security;

import java.lang.annotation.*;

/**
 * 角色权限注解：后端强制校验，防止越权访问（前端按钮隐藏只是第一层）。
 * 需网关已注入 X-User-Role 头（UserHeaderInterceptor 填充上下文）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    String[] value();
}
