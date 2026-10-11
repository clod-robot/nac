package com.nac.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HashiCorp Vault KEK 提供者（Strategy + Adapter）：通过 KV v2 REST 读取主密钥，KEK 不离开 Vault 访问控制。
 * 仅当配置 nac.security.kek.vault.addr 时生效，priority=80。未配置则不注册，自动降级。
 * 配置：vault.addr / vault.token / vault.mount(默认 secret) / vault.path / vault.field(默认 kek)。
 */
@Component
@ConditionalOnProperty(name = "nac.security.kek.vault.addr")
public class VaultKekProvider extends AbstractHttpKekProvider {

    @Value("${nac.security.kek.vault.addr:}")
    private String addr;
    @Value("${nac.security.kek.vault.token:}")
    private String token;
    @Value("${nac.security.kek.vault.mount:secret}")
    private String mount;
    @Value("${nac.security.kek.vault.path:}")
    private String path;
    @Value("${nac.security.kek.vault.field:kek}")
    private String field;

    @Override
    public int priority() {
        return 80;
    }

    @Override
    protected String endpoint() {
        String base = addr.endsWith("/") ? addr.substring(0, addr.length() - 1) : addr;
        String p = path.startsWith("/") ? path.substring(1) : path;
        return base + "/v1/" + mount + "/data/" + p;
    }

    @Override
    protected Map<String, String> headers() {
        return Map.of("X-Vault-Token", token);
    }

    @Override
    protected String extractKey(String body) {
        // KV v2: {"data":{"data":{"<field>":"..."}}}；用正则避免引入 JSON 依赖
        Pattern pat = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = pat.matcher(body);
        return m.find() ? m.group(1) : null;
    }
}
