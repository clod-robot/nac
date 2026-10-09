package com.his.common.ratelimit;

import java.lang.annotation.*;

/**
 * 接口限流注解：基于 Redis + Lua 原子计数。
 * 可指定维度：IP / USER / 默认（方法+IP）。防刷核心组件。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /** 时间窗口（秒） */
    int window() default 60;
    /** 窗口内最大请求数 */
    int limit() default 60;
    /** 限流维度 */
    Dimension dimension() default Dimension.IP;

    enum Dimension { IP, USER, GLOBAL }
}
