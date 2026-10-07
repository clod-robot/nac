package com.nac.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 安全响应头过滤器：CSP / X-Frame-Options / nosniff / no-referrer / no-store。
 */
@Component
public class SecurityHeaderGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders h = exchange.getResponse().getHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Cache-Control", "no-store");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
