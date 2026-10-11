package com.nac.common.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密钥信封加密（AES-256-GCM）：内部签名密钥等敏感配置以密文落库/落缓存，运行时在内存解密。
 * 主密钥(KEK)由 KekProviderRegistry 责任链提供（Strategy：env/file/vault/hsm/cloud；
 * 按 priority 降序选首个可用，全部不可用降级默认 KEK 并告警）。不绑定具体厂商。
 * 密文带 enc:v1: 前缀，未带前缀的历史明文按原样返回以平滑迁移。
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String PREFIX = "enc:v1:";
    private static final String DEFAULT_KEK = "NacDefaultKek@2026-ChangeMe";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final boolean defaultKek;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(KekProviderRegistry registry) {
        String material = registry.resolveFirstAvailable();
        this.defaultKek = (material == null);
        String k = material == null ? DEFAULT_KEK : material;
        try {
            byte[] dk = MessageDigest.getInstance("SHA-256").digest(k.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(dk, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("KEK 派生失败", e);
        }
    }

    @PostConstruct
    void warn() {
        if (defaultKek) {
            log.error("【安全告警】无可用 KEK 提供者，敏感配置加密使用默认主密钥，不符合密评要求，请配置 NAC_KEK 或接入 KMS/密码机！");
        }
    }

    /** 加密：返回 enc:v1: + base64(iv || ciphertext+tag) */
    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 解密：带前缀则解密，否则按历史明文原样返回（平滑迁移）；解密失败返回 null 由调用方兜底 */
    public String decrypt(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        if (!token.startsWith(PREFIX)) {
            return token; // 历史明文，兼容
        }
        try {
            byte[] raw = Base64.getDecoder().decode(token.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(raw, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(raw, IV_LEN, raw.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("敏感配置解密失败（KEK 可能不一致），丢弃该值: {}", e.getMessage());
            return null;
        }
    }
}
