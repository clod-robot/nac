package com.nac.gateway.filter;

import com.nac.common.security.HmacUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关签名过滤器：对转发到下游的请求加 HMAC-SHA256 签名（ts+method+path），
 * 下游 GatewayTrustFilter 校验，防绕过网关直连。
 */
@Slf4j
@Component
public class SignGlobalFilter implements GlobalFilter, Ordered {

    private static final String DEFAULT_SECRET = "NacInternal@2026-Gw2Svc";

    @Autowired
    private HmacUtil hmacUtil;

    @Value("${nac.gateway.internal-secret:${NAC_INTERNAL_SECRET:NacInternal@2026-Gw2Svc}}")
    private String internalSecret;

    @PostConstruct
    void warnDefaultSecret() {
        if (DEFAULT_SECRET.equals(internalSecret)) {
            log.error("【安全告警】网关内部签名密钥仍为出厂默认值，存在签名伪造/重放风险，请立即通过 NAC_INTERNAL_SECRET 修改并与各服务保持一致！");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
        String method = request.getMethod() == null ? "" : request.getMethod().name();
        String path = request.getURI().getPath();
        // nonce 参与签名，下游据此去重，防窗口内重放
        String sign = hmacUtil.sign(internalSecret, ts, method, path, nonce);

        ServerHttpRequest mutated = request.mutate()
                .header("X-Gateway-Ts", ts)
                .header("X-Gateway-Nonce", nonce)
                .header("X-Gateway-Sign", sign)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
