package com.his.common.interceptor;

import com.his.common.context.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 从网关注入的 X-User-* 头填充用户上下文（网关已剥离客户端伪造值，可信）。
 */
@Component
public class UserHeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            UserContext.set(new UserContext.CurrentUser(
                    Long.valueOf(userId),
                    request.getHeader("X-User-Name"),
                    request.getHeader("X-User-Role")));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
