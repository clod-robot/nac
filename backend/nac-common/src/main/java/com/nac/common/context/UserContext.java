package com.nac.common.context;

/**
 * 当前请求用户上下文（ThreadLocal），由网关注入的 X-User-* 头经拦截器填充。
 * 使用完毕务必在拦截器 afterCompletion 中 remove，避免线程池复用导致信息串号。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private UserContext() {}

    public static void set(Long userId, String username, String role) {
        USER_ID.set(userId);
        USERNAME.set(username);
        ROLE.set(role);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static boolean isAdmin() {
        String role = ROLE.get();
        return "admin".equals(role) || "超级管理员".equals(role)
                || "系统管理员".equals(role) || "运维管理员".equals(role);
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        ROLE.remove();
    }
}
