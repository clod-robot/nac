package com.nac.common.result;

/**
 * 统一返回状态码。
 */
public enum ResultCode {
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    SERVER_ERROR(500, "服务器内部错误"),

    CAPTCHA_INVALID(1001, "图形验证码错误或已失效"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    ACCOUNT_LOCKED(1003, "账号已锁定，请稍后再试"),
    IP_LOCKED(1004, "当前 IP 登录失败过多，已临时锁定"),
    SMS_CODE_INVALID(1005, "短信验证码错误或已失效"),
    SMS_COOLDOWN(1006, "短信发送过于频繁，请 60 秒后重试"),
    SMS_DAILY_LIMIT(1007, "今日短信发送次数已达上限"),
    SMS_GATEWAY_ERROR(1008, "短信网关发送失败"),
    IDEMPOTENT_REPEAT(1009, "请勿重复提交"),
    PHONE_FORMAT_ERROR(1010, "手机号格式错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
