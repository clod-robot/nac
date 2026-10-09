package com.his.common.security;

import org.apache.commons.lang3.StringUtils;

/**
 * 敏感数据脱敏工具：身份证、手机号、姓名等展示时脱敏（等保合规要求）。
 */
public class SensitiveUtil {

    private SensitiveUtil() {}

    /** 手机号：138****1234 */
    public static String maskPhone(String phone) {
        if (StringUtils.isBlank(phone) || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 身份证：110101********1234 */
    public static String maskIdCard(String idCard) {
        if (StringUtils.isBlank(idCard) || idCard.length() < 10) return idCard;
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /** 姓名：张*，欧阳** */
    public static String maskName(String name) {
        if (StringUtils.isBlank(name)) return name;
        if (name.length() == 1) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + repeat("*", name.length() - 2) + name.charAt(name.length() - 1);
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
