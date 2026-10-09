package com.his.common.filter;

import com.his.common.security.HmacUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 网关信任过滤器：校验请求是否来自网关（HMAC 签名 + 时间窗）。
 * 直连服务端口（绕过网关）将返回 403。可通过 his.gateway.trust-enabled=false 关闭（本地调试用）。
 */
@Configuration
public class GatewayTrustFilter {

    @Value("${his.gateway.internal-secret:${NAC_INTERNAL_SECRET:HisInternal@2026-Gw2Svc}}")
    private String internalSecret;

    @Value("${his.gateway.trust-enabled:true}")
    private boolean trustEnabled;

    @Value("${his.gateway.trust-whitelist:/actuator/**}")
    private String whitelist;

    @Bean
    public FilterRegistrationBean<Filter> gatewayTrustFilterRegistration() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                HttpServletResponse resp = (HttpServletResponse) response;
                if (!trustEnabled || isWhitelisted(req.getRequestURI())) {
                    chain.doFilter(request, response);
                    return;
                }
                String ts = req.getHeader("X-Gateway-Ts");
                String sign = req.getHeader("X-Gateway-Sign");
                if (!HmacUtil.verify(internalSecret, ts, req.getMethod(), req.getRequestURI(), sign)) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.getWriter().write("{\"code\":403,\"message\":\"禁止直连服务端口，请通过网关访问\"}");
                    return;
                }
                chain.doFilter(request, response);
            }
        });
        reg.addUrlPatterns("/*");
        reg.setName("gatewayTrustFilter");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return reg;
    }

    private boolean isWhitelisted(String uri) {
        for (String p : whitelist.split(",")) {
            String pattern = p.trim().replace("**", ".*");
            if (uri.matches(pattern)) return true;
        }
        return false;
    }
}
