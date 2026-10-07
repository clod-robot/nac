package com.nac.common.constant;

/**
 * Redis key 常量（统一前缀 nac:）。
 */
public interface RedisKeyConstants {
    /** 图形验证码：nac:captcha:{uuid} */
    String CAPTCHA_PREFIX = "nac:captcha:";
    /** 登录失败计数 */
    String LOGIN_FAIL_IP_PREFIX = "nac:login:fail:ip:";
    String LOGIN_FAIL_USER_PREFIX = "nac:login:fail:user:";
    /** 登录锁定 */
    String LOGIN_LOCK_IP_PREFIX = "nac:login:lock:ip:";
    String LOGIN_LOCK_USER_PREFIX = "nac:login:lock:user:";
    /** 登录幂等锁 traceId */
    String LOGIN_IDEMPOTENT_PREFIX = "nac:login:idem:";
    /** token 白名单：nac:token:{userId} -> token */
    String TOKEN_PREFIX = "nac:token:";
    /** 幂等切面 */
    String IDEMPOTENT_PREFIX = "nac:idem:";
    /** 限流切面 */
    String RATE_LIMIT_PREFIX = "nac:rl:";
    /** 短信验证码盲索引 key 前缀（value 不存明文手机号） */
    String SMS_CODE_PREFIX = "nac:sms:code:";
    /** 短信冷却：nac:sms:cd:{blindIndex} */
    String SMS_COOLDOWN_PREFIX = "nac:sms:cd:";
    /** 短信每日计数：nac:sms:cnt:{date}:{blindIndex} */
    String SMS_DAILY_COUNT_PREFIX = "nac:sms:cnt:";
    /** 短信验证码失败次数 */
    String SMS_FAIL_PREFIX = "nac:sms:fail:";
    /** 在线会话（脱敏用户名） */
    String ONLINE_SESSION_PREFIX = "nac:online:";
    /** RADIUS 与 NAS 约定的共享密钥（Redis 持久化，可在 NAS 管理页热修改） */
    String RADIUS_SHARED_SECRET = "nac:radius:shared-secret";
    /** 短信网关配置（provider + 各云凭证，JSON，Redis 持久化，可热修改） */
    String SMS_CONFIG = "nac:sms:config";
    /** 日志外发 syslog 服务器配置（JSON，Redis 持久化，可热修改） */
    String SYSLOG_CONFIG = "nac:syslog:config";
    /** 日志未过滤总数缓存 */
    String LOG_COUNT_ALL = "nac:log:count:all";
    /** 进程内缓存通用 TTL（秒） */
    int LOCAL_CACHE_TTL = 30;
}
