package com.his.common.context;

/**
 * 当前登录用户上下文（ThreadLocal，线程池异步场景需手动传递）
 */
public class UserContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String getUsername() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUsername();
    }

    public static String getRole() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static class CurrentUser {
        private Long userId;
        private String username;
        private String role;

        public CurrentUser() {}
        public CurrentUser(Long userId, String username, String role) {
            this.userId = userId; this.username = username; this.role = role;
        }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
