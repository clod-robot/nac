package com.his.gateway.filter;

import com.his.common.constant.RedisKeyConstants;
import com.his.common.security.HmacUtil;
import com.his.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 网关鉴权全局过滤器：
 * 1. 白名单放行（登录、验证码、健康检查）
 * 2. 校验 Authorization Bearer Token：JWT 签名 + Redis 白名单（支持登出即时失效）
 * 3. 剥离客户端伪造的 X-User-* 头，重新注入（防头篡改）
 * 4. 滑动续期 token TTL
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${his.gateway.internal-secret:${NAC_INTERNAL_SECRET:HisInternal@2026-Gw2Svc}}")
    private String internalSecret;

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 白名单：无需 token */
    private static final List<String> WHITELIST = Arrays.asList(
            "/api/auth/login",
            "/api/auth/captcha/**",
            "/api/auth/logout",
            "/actuator/health",
            "/doc.html",
            "/swagger-ui/**",
            "/v2/api-docs/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhitelist(path)) {
            return chain.filter(exchange.mutate().request(cleanHeaders(request)).build());
        }

        String token = resolveToken(request);
        if (token == null || !jwtUtil.validate(token)) {
            return unauthorized(exchange, "未登录或登录已过期");
        }
        // Redis 白名单校验：登出时删除 key 即失效
        String userId;
        try {
            Claims claims = jwtUtil.parse(token);
            userId = claims.getSubject();
        } catch (Exception e) {
            return unauthorized(exchange, "Token 无效");
        }
        Boolean exists = redisTemplate.hasKey(RedisKeyConstants.TOKEN_PREFIX + userId);
        if (!Boolean.TRUE.equals(exists)) {
            return unauthorized(exchange, "登录已失效，请重新登录");
        }
        // 滑动续期
        redisTemplate.expire(RedisKeyConstants.TOKEN_PREFIX + userId,
                java.time.Duration.ofSeconds(jwtUtil.getExpireSeconds()));

        // 注入可信用户头（剥离客户端伪造值）
        ServerHttpRequest mutated = cleanHeaders(request).mutate()
                .header("X-User-Id", userId)
                .header("X-User-Name", jwtUtil.getUsername(token))
                .header("X-User-Role", jwtUtil.getRole(token))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private ServerHttpRequest cleanHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Name");
                    h.remove("X-User-Role");
                    h.remove("X-Gateway-*");
                })
                .build();
    }

    private boolean isWhitelist(String path) {
        return WHITELIST.stream().anyMatch(p -> matcher.match(p, path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        // SSE/下载场景支持 ?token=
        List<String> q = request.getQueryParams().get("token");
        return q != null && !q.isEmpty() ? q.get(0) : null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        log.warn("网关鉴权拒绝: {}", msg);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
