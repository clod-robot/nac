package com.his.common.idempotent;

import java.lang.annotation.*;

/**
 * 幂等注解：基于 Redis SETNX 实现，防止重复提交（挂号、缴费、发药等场景）。
 * key 支持 SpEL 表达式，默认使用 traceId + 方法签名。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    /** 幂等 key 的 SpEL 表达式，如 "#req.patientId + '_' + #req.scheduleId" */
    String key() default "";
    /** 幂等窗口（秒），默认 10 秒 */
    int expire() default 10;
    /** 提示信息 */
    String message() default "请勿重复提交";
}
