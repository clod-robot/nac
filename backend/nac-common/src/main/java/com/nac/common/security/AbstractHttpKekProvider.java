package com.nac.common.security;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * REST 型 KEK 提供者模板（Template Method）：用 JDK HttpClient 拉取密钥，子类定义端点/鉴权/解析。
 * 供 Vault、云 KMS 等 HTTP 形态复用，避免重复的连接、超时、异常处理代码。
 */
@Slf4j
public abstract class AbstractHttpKekProvider implements KekProvider {

    protected abstract String endpoint();

    protected abstract Map<String, String> headers();

    /** 从响应体解析出 KEK 明文，失败返回 null */
    protected abstract String extractKey(String responseBody);

    @Override
    public String getKekMaterial() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(endpoint())).timeout(Duration.ofSeconds(5));
            headers().forEach(rb::header);
            HttpResponse<String> resp = client.send(rb.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("{} 拉取 KEK 返回 HTTP {}", name(), resp.statusCode());
                return null;
            }
            return extractKey(resp.body());
        } catch (Exception e) {
            log.warn("{} 拉取 KEK 失败: {}", name(), e.getMessage());
            return null;
        }
    }

    protected String name() {
        return getClass().getSimpleName();
    }
}
