package com.nac.common.security;

/**
 * 敏感信息脱敏工具。
 */
public final class SensitiveUtil {

    private SensitiveUtil() {}

    /** 手机号脱敏：前 3 后 4，中间 ****，例 138****1234 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 通用脱敏：保留前 pre 后 suf 位 */
    public static String mask(String value, int pre, int suf) {
        if (value == null) return null;
        int len = value.length();
        if (len <= pre + suf) return "****";
        return value.substring(0, pre) + "****" + value.substring(len - suf);
    }
}
