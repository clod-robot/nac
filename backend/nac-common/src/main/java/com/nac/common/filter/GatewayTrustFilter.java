package com.nac.common.filter;

import com.nac.common.security.HmacUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * 网关信任过滤器：校验请求是否来自网关（HMAC 签名 + 时间窗 ±300s）。
 * 直连服务端口（绕过网关）返回 403。本地调试可设 nac.gateway.trust-enabled=false 关闭。
 */
@Configuration
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GatewayTrustFilter {

    @Value("${nac.gateway.internal-secret:${NAC_INTERNAL_SECRET:NacInternal@2026-Gw2Svc}}")
    private String internalSecret;

    @Value("${nac.gateway.trust-enabled:true}")
    private boolean trustEnabled;

    private final HmacUtil hmacUtil;

    public GatewayTrustFilter(HmacUtil hmacUtil) {
        this.hmacUtil = hmacUtil;
    }

    @Bean
    public FilterRegistrationBean<Filter> gatewayTrustFilterRegistration() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                HttpServletResponse resp = (HttpServletResponse) response;
                if (!trustEnabled) {
                    chain.doFilter(request, response);
                    return;
                }
                String ts = req.getHeader("X-Gateway-Ts");
                String sign = req.getHeader("X-Gateway-Sign");
                if (!hmacUtil.verify(internalSecret, ts, req.getMethod(), req.getRequestURI(), sign)) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.setContentType("application/json;charset=UTF-8");
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
}
