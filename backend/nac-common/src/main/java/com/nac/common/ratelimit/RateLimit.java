package com.nac.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解（Redis + Lua 原子计数）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 时间窗口内最大次数 */
    int limit();
    /** 时间窗口（秒），默认 60 */
    int window() default 60;
    /** 限流维度 */
    Dimension dimension() default Dimension.IP;

    enum Dimension { IP, USER, GLOBAL }
}
