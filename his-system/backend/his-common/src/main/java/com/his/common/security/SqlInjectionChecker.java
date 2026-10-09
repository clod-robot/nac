package com.his.common.security;

import java.util.regex.Pattern;

/**
 * SQL 注入检测：对拼接查询参数做关键字过滤。
 * 注：MyBatis 全部使用 #{} 预编译参数已是主防线，本工具用于
 * 动态排序字段、模糊搜索等无法使用预编译的边界场景做二次校验。
 */
public class SqlInjectionChecker {

    private SqlInjectionChecker() {}

    private static final Pattern[] DANGEROUS = {
            Pattern.compile("(['\";])?\\s*(or|and)\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(union\\s+select|select\\s+.*\\s+from|insert\\s+into|delete\\s+from|drop\\s+table|update\\s+.*\\s+set|exec\\s*\\(|execute\\s*\\(|xp_cmdshell)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(--|#|/\\*|\\*/|;)", Pattern.CASE_INSENSITIVE)
    };

    public static boolean check(String value) {
        if (value == null || value.isEmpty()) return true;
        for (Pattern p : DANGEROUS) {
            if (p.matcher(value).find()) return false;
        }
        return true;
    }

    /** 校验失败抛出 IllegalArgumentException */
    public static void validate(String field, String value) {
        if (!check(value)) {
            throw new IllegalArgumentException("参数 [" + field + "] 含有非法字符");
        }
    }

    /**
     * 动态排序字段白名单校验：只允许字母、数字、下划线、逗号，防止 order by 注入
     */
    public static String safeOrderBy(String orderBy) {
        if (orderBy == null) return null;
        if (!orderBy.matches("^[a-zA-Z0-9_,\\s]+$")) {
            throw new IllegalArgumentException("排序参数非法");
        }
        return orderBy;
    }
}
