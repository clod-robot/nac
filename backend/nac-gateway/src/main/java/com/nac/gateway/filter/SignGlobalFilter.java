package com.nac.gateway.filter;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.security.HmacUtil;
import com.nac.common.security.SecretCipher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关签名过滤器：对转发到下游的请求加 HMAC-SHA256 签名（ts+method+path），
 * 下游 GatewayTrustFilter 校验，防绕过网关直连。
 */
@Slf4j
@Component
@EnableScheduling
public class SignGlobalFilter implements GlobalFilter, Ordered {

    private static final String DEFAULT_SECRET = "NacInternal@2026-Gw2Svc";

    @Autowired
    private HmacUtil hmacUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SecretCipher cipher;

    @Value("${nac.gateway.internal-secret:${NAC_INTERNAL_SECRET:NacInternal@2026-Gw2Svc}}")
    private String internalSecret;

    /** 生效密钥（current 明文）：从共享 Redis 解密加载，失败/为空回退 @Value 默认；volatile 保证请求线程可见 */
    private volatile String effectiveSecret;

    @PostConstruct
    void initSecret() {
        refreshEffectiveSecret();
        if (DEFAULT_SECRET.equals(effectiveSecret)) {
            log.error("【安全告警】网关内部签名密钥仍为出厂默认值，存在签名伪造/重放风险，请立即在【系统参数设置】中修改并与各服务保持一致！");
        }
    }

    /** 周期热加载（每 10s）：从共享 Redis 读取密文并解密；失败/空值/解密失败时保留旧值绝不置空 */
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    void refreshEffectiveSecret() {
        try {
            String raw = redisTemplate.opsForValue().get(RedisKeyConstants.GW_INTERNAL_SECRET);
            String v = (raw == null || raw.isBlank()) ? null : cipher.decrypt(raw);
            if (v != null && !v.isBlank()) {
                this.effectiveSecret = v;
            } else if (this.effectiveSecret == null) {
                this.effectiveSecret = internalSecret;
            }
        } catch (Exception e) {
            log.debug("刷新网关内部签名密钥失败，沿用当前值: {}", e.getMessage());
            if (this.effectiveSecret == null) {
                this.effectiveSecret = internalSecret;
            }
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
        String sign = hmacUtil.sign(effectiveSecret, ts, method, path, nonce);

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
