package com.his.common.constant;

public interface RedisKeyConstants {
    String CAPTCHA_PREFIX = "his:captcha:";
    String LOGIN_FAIL_IP_PREFIX = "his:login:fail:ip:";
    String LOGIN_FAIL_USER_PREFIX = "his:login:fail:user:";
    String LOGIN_LOCK_IP_PREFIX = "his:login:lock:ip:";
    String LOGIN_LOCK_USER_PREFIX = "his:login:lock:user:";
    String TOKEN_PREFIX = "his:token:";
    String IDEMPOTENT_PREFIX = "his:idem:";
    String RATE_LIMIT_PREFIX = "his:rl:";
    /** 号源库存缓存：scheduleId -> 剩余号数 */
    String SCHEDULE_STOCK_PREFIX = "his:schedule:stock:";
    /** 药品库存缓存（30s 进程内缓存，DB 为准） */
    int STOCK_CACHE_TTL = 30;
}
