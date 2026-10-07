package com.nac.common.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解（Redis SETNX）。用于防重复提交。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /** key 前缀（业务区分） */
    String key() default "";
    /** 锁 TTL（秒） */
    int ttl() default 10;
}
