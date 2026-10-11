package com.nac.gateway.filter;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.security.JwtUtil;
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
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 网关鉴权全局过滤器：
 * 1. 白名单放行（登录、验证码、free-auth）；/api/portal/** 仅 GET 匿名放行
 * 2. 校验 Bearer Token：JWT 签名 + Redis 白名单（登出即时失效）+ 滑动续期 8h
 * 3. 剥离客户端伪造 X-User-* 头，重新注入可信值（防头篡改越权）
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${nac.gateway.whitelist:/api/auth/login,/api/auth/captcha/**,/api/auth/sms/send,/api/auth/sms/login,/api/portal/auth,/free-auth/check}")
    private String whitelist;

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() == null ? "" : request.getMethod().name();

        // portal：公开接口匿名放行；/api/portal/admin/** 为管理接口，必须走下方 JWT 校验并注入角色头
        if (matcher.match("/api/portal/**", path)) {
            boolean adminApi = matcher.match("/api/portal/admin/**", path);
            boolean publicGet = "GET".equalsIgnoreCase(method);                       // /api/portal/config 公开读取
            boolean publicAuth = "POST".equalsIgnoreCase(method) && matcher.match("/api/portal/auth", path);
            if (!adminApi && (publicGet || publicAuth)) {
                return chain.filter(exchange.mutate().request(cleanHeaders(request)).build());
            }
            if (!adminApi) {
                return unauthorized(exchange, "Portal 写接口需登录");
            }
            // 管理接口继续向下执行 JWT 校验
        }

        if (isWhitelist(path)) {
            return chain.filter(exchange.mutate().request(cleanHeaders(request)).build());
        }

        String token = resolveToken(request);
        if (token == null || !jwtUtil.validate(token)) {
            return unauthorized(exchange, "未登录或登录已过期");
        }
        String userId;
        try {
            Claims claims = jwtUtil.parse(token);
            userId = claims.getSubject();
        } catch (Exception e) {
            return unauthorized(exchange, "Token 无效");
        }
        final String uid = userId;
        final long expireSec = jwtUtil.getExpireSeconds();
        // Redis 是阻塞 IO，绝不能在 Netty 事件环上执行——切到 boundedElastic 线程池，
        // 否则并发一高事件环被占满，整个网关吞吐塌陷、请求大面积超时。
        return Mono.fromCallable(() -> {
            String key = RedisKeyConstants.TOKEN_PREFIX + uid;
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) return null;
            redisTemplate.expire(key, Duration.ofSeconds(expireSec));
            return uid;
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(id -> {
            if (id == null) return unauthorized(exchange, "登录已失效，请重新登录");
            ServerHttpRequest mutated = cleanHeaders(request).mutate()
                    .header("X-User-Id", id)
                    .header("X-User-Name", jwtUtil.getUsername(token))
                    .header("X-User-Role", jwtUtil.getRole(token))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        });
    }

    private ServerHttpRequest cleanHeaders(ServerHttpRequest request) {
        return request.mutate().headers(h -> {
            h.remove("X-User-Id");
            h.remove("X-User-Name");
            h.remove("X-User-Role");
        }).build();
    }

    private boolean isWhitelist(String path) {
        List<String> list = Arrays.asList(whitelist.split(","));
        return list.stream().anyMatch(p -> matcher.match(p.trim(), path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
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
