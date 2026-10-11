package com.nac.common.filter;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.security.HmacUtil;
import com.nac.common.security.SecretCipher;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;

/**
 * 网关信任过滤器：校验请求是否来自网关（HMAC 签名 + 时间窗 ±300s）。
 * 直连服务端口（绕过网关）返回 403。本地调试可设 nac.gateway.trust-enabled=false 关闭。
 * 支持双版本平滑轮换：验签接受 current + previous，切换窗口不中断；缓存未命中时同步刷新一次。
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GatewayTrustFilter {

    @Value("${nac.gateway.internal-secret:${NAC_INTERNAL_SECRET:NacInternal@2026-Gw2Svc}}")
    private String internalSecret;

    @Value("${nac.gateway.trust-enabled:true}")
    private boolean trustEnabled;

    private final HmacUtil hmacUtil;
    private final SecretCipher cipher;
    private final StringRedisTemplate redisTemplate;

    /** current / previous 明文密钥，volatile 保证请求线程可见；周期热刷新，失败保留旧值绝不置空 */
    private volatile String currentSecret;
    private volatile String prevSecret;

    public GatewayTrustFilter(HmacUtil hmacUtil, SecretCipher cipher, StringRedisTemplate redisTemplate) {
        this.hmacUtil = hmacUtil;
        this.cipher = cipher;
        this.redisTemplate = redisTemplate;
    }

    /** 从共享 Redis 读取密文并解密到 current/prev；任一失败保留旧值，绝不置空 */
    private void reloadFromRedis() {
        try {
            String raw = redisTemplate.opsForValue().get(RedisKeyConstants.GW_INTERNAL_SECRET);
            String cur = (raw == null || raw.isBlank()) ? null : cipher.decrypt(raw);
            if (cur != null && !cur.isBlank()) {
                this.currentSecret = cur;
            } else if (this.currentSecret == null) {
                this.currentSecret = internalSecret;
            }
        } catch (Exception e) {
            log.debug("刷新 current 密钥失败，沿用当前值: {}", e.getMessage());
            if (this.currentSecret == null) {
                this.currentSecret = internalSecret;
            }
        }
        try {
            String raw = redisTemplate.opsForValue().get(RedisKeyConstants.GW_INTERNAL_SECRET_PREV);
            String prev = (raw == null || raw.isBlank()) ? null : cipher.decrypt(raw);
            if (prev != null && !prev.isBlank()) {
                this.prevSecret = prev;
            } else if (raw != null && raw.isBlank()) {
                this.prevSecret = null; // 显式空表示无旧版本
            }
        } catch (Exception e) {
            log.debug("刷新 previous 密钥失败: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    void refreshEffectiveSecret() {
        reloadFromRedis();
    }

    /** 验签（含平滑轮换）：先 current，再 previous；均失败则同步刷新一次再重试，仍失败才拒绝 */
    private boolean verifyRotation(String ts, String method, String uri, String nonce, String sign) {
        String cur = currentSecret;
        if (cur != null && hmacUtil.verify(cur, ts, method, uri, nonce, sign)) {
            return true;
        }
        String prev = prevSecret;
        if (prev != null && hmacUtil.verify(prev, ts, method, uri, nonce, sign)) {
            return true;
        }
        // 缓存可能滞后于轮换：同步刷新一次再试（仅在验签失败路径，不在每请求热路径）
        reloadFromRedis();
        cur = currentSecret;
        if (cur != null && hmacUtil.verify(cur, ts, method, uri, nonce, sign)) {
            return true;
        }
        prev = prevSecret;
        return prev != null && hmacUtil.verify(prev, ts, method, uri, nonce, sign);
    }

    @Bean
    public FilterRegistrationBean<Filter> gatewayTrustFilterRegistration() {
        reloadFromRedis(); // 启动初始加载，保证绝不置空
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
                // WebSocket 路径放行：其鉴权由握手后的 JWT 在处理器内完成（浏览器无法携带网关 HMAC 头）
                String uri = req.getRequestURI();
                if (uri != null && uri.startsWith("/ws/")) {
                    chain.doFilter(request, response);
                    return;
                }
                String ts = req.getHeader("X-Gateway-Ts");
                String nonce = req.getHeader("X-Gateway-Nonce");
                String sign = req.getHeader("X-Gateway-Sign");
                if (!verifyRotation(ts, req.getMethod(), req.getRequestURI(), nonce, sign)) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"code\":403,\"message\":\"禁止直连服务端口，请通过网关访问\"}");
                    return;
                }
                // 重放防护：同一 ts+nonce 在时间窗内只能出现一次（Redis 原子去重）
                if (nonce != null) {
                    Boolean first = redisTemplate.opsForValue()
                            .setIfAbsent("gw:nonce:" + ts + ":" + nonce, "1", Duration.ofSeconds(310));
                    if (!Boolean.TRUE.equals(first)) {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        resp.setContentType("application/json;charset=UTF-8");
                        resp.getWriter().write("{\"code\":403,\"message\":\"重复请求\"}");
                        return;
                    }
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
