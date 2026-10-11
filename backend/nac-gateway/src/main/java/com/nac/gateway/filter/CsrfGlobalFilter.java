package com.nac.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 网关 CSRF 防护（Double Submit Cookie）：
 * 1. 登录后下发非 HttpOnly 的 XSRF-TOKEN cookie（JS 可读）；
 * 2. 前端在请求头 X-XSRF-TOKEN 回传该值，网关在「非安全方法且非白名单」上校验二者一致；
 * 3. 配合 NAC_TOKEN 的 SameSite=Lax，形成对跨站请求伪造的纵深防御。
 * 白名单为无需登录的公开写接口（无会话则 CSRF 无意义，且登录前必须可写）。
 */
@Slf4j
@Component
public class CsrfGlobalFilter implements GlobalFilter, Ordered {

    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final Duration MAX_AGE = Duration.ofHours(8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 公开写接口白名单（与鉴权白名单保持一致），登录前必须可访问 */
    private static final List<String> WHITELIST = Arrays.asList(
            "/api/auth/login",
            "/api/auth/captcha/**",
            "/api/auth/sms/send",
            "/api/auth/sms/login",
            "/api/portal/auth",
            "/free-auth/check"
    );

    private static final List<String> SAFE_METHODS = Arrays.asList("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() == null ? "" : request.getMethod().name().toUpperCase();

        String existing = cookieValue(request, XSRF_COOKIE);
        // 没有则生成新 token，响应阶段下发
        final String token = (existing != null && !existing.isEmpty()) ? existing : generateToken();

        boolean unsafe = !SAFE_METHODS.contains(method);
        boolean whitelisted = WHITELIST.stream().anyMatch(p -> matcher.match(p, path));
        if (unsafe && !whitelisted) {
            String header = request.getHeaders().getFirst(XSRF_HEADER);
            if (header == null || !constantTimeEquals(header, existing)) {
                log.warn("网关 CSRF 拒绝: method={} path={}", method, path);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        // 响应提交前写入/刷新 XSRF cookie（此时 headers 尚未固化，可安全写入）
        if (!token.equals(existing)) {
            exchange.getResponse().addCookie(ResponseCookie.from(XSRF_COOKIE, token)
                    .path("/")
                    .httpOnly(false)          // 前端需读取后回传
                    .secure(isSecure(request))
                    .sameSite("Lax")
                    .maxAge(MAX_AGE)
                    .build());
        }
        return chain.filter(exchange);
    }

    private boolean isSecure(ServerHttpRequest request) {
        if ("https".equalsIgnoreCase(request.getURI().getScheme())) return true;
        String proto = request.getHeaders().getFirst("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(proto);
    }

    private String cookieValue(ServerHttpRequest request, String name) {
        String cookie = request.getHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        String prefix = name + "=";
        for (String c : cookie.split(";")) {
            String t = c.trim();
            if (t.startsWith(prefix)) return t.substring(prefix.length());
        }
        return null;
    }

    private String generateToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    @Override
    public int getOrder() {
        // 晚于 AuthGlobalFilter(-100)：未登录请求先被鉴权拦截，已登录请求再校验 CSRF
        return -50;
    }
}
