package com.his.gateway.filter;

import com.his.common.security.HmacUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关签名过滤器：对转发到下游的请求附加 HMAC-SHA256 签名头，
 * 下游服务通过 GatewayTrustFilter 校验，阻止绕过网关直连端口。
 */
@Component
public class SignGlobalFilter implements GlobalFilter, Ordered {

    @Value("${his.gateway.internal-secret:${NAC_INTERNAL_SECRET:HisInternal@2026-Gw2Svc}}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String method = request.getMethodValue();
        String path = request.getURI().getPath();
        String sign = HmacUtil.sign(internalSecret, ts, method, path);
        ServerHttpRequest mutated = request.mutate()
                .header("X-Gateway-Ts", ts)
                .header("X-Gateway-Sign", sign)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
