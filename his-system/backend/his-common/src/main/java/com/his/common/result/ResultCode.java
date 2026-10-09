package com.his.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    IDEMPOTENT_CONFLICT(409, "请勿重复提交"),
    CAPTCHA_ERROR(1001, "验证码错误或已失效"),
    LOGIN_LOCKED(1002, "登录失败次数过多，账号已锁定"),
    USERNAME_PASSWORD_ERROR(1003, "用户名或密码错误"),
    STOCK_NOT_ENOUGH(2001, "药品库存不足"),
    REGISTRATION_FULL(2002, "号源已满"),
    DUPLICATE_REGISTRATION(2003, "请勿重复挂号");

    private final int code;
    private final String message;
}
