package com.nac.common.interceptor;

import com.nac.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从网关注入的可信头填充用户上下文；请求结束清理，避免线程池复用串号。
 */
public class UserHeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uid = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Name");
        String role = request.getHeader("X-User-Role");
        if (uid != null && !uid.isEmpty()) {
            try {
                UserContext.set(Long.valueOf(uid), username, role);
            } catch (NumberFormatException ignored) {
                UserContext.set(null, username, role);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
