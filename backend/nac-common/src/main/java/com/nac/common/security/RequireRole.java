package com.nac.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色要求注解（防越权）。标注的方法需当前用户具备指定角色。
 * admin 白名单：admin / 超级管理员 / 系统管理员 / 运维管理员。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /** 允许的角色标识，满足其一即可通过；为空时要求 admin 白名单 */
    String[] value() default {};
}
