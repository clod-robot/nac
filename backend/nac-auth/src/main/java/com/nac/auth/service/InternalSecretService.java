package com.nac.auth.service;

import com.nac.auth.mapper.ConfigMapper;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.entity.SysConfig;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.ConfigCache;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.ResultCode;
import com.nac.common.security.SecretCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * 网关内部签名密钥管理（cache-aside）：sys_config 为真值，Redis 缓存；敏感值以密文存储。
 * 支持双版本平滑轮换：current 为生效值，previous 为上一版，下游验签同时接受两者，切换窗口不中断。
 */
@Slf4j
@Service
public class InternalSecretService {

    /** 出厂默认密钥（不安全，仅供兜底，必须在部署后替换） */
    public static final String DEFAULT_SECRET = "NacInternal@2026-Gw2Svc";

    private static final String K = RedisKeyConstants.GW_INTERNAL_SECRET;
    private static final String K_PREV = RedisKeyConstants.GW_INTERNAL_SECRET_PREV;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int GEN_LEN = 32;
    private static final int MIN_LEN = 16;

    private final RedisUtil redisUtil;
    private final ConfigMapper configMapper;
    private final SecretCipher cipher;

    @Value("${nac.gateway.internal-secret:${NAC_INTERNAL_SECRET:" + DEFAULT_SECRET + "}}")
    private String fallbackSecret;

    public InternalSecretService(RedisUtil redisUtil, ConfigMapper configMapper, SecretCipher cipher) {
        this.redisUtil = redisUtil;
        this.configMapper = configMapper;
        this.cipher = cipher;
    }

    /** 读取并解密指定 key（缓存优先，未命中查库回填）；返回明文，空则 null */
    private String readDecrypted(String key) {
        String raw = ConfigCache.get(redisUtil, key,
                () -> { SysConfig sc = configMapper.selectByKey(key); return sc == null ? null : sc.getConfigValue(); },
                null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return cipher.decrypt(raw);
    }

    /** 生效（current）密钥明文：缓存/库优先，空值回退 env 默认 */
    public String resolve() {
        String v = readDecrypted(K);
        return (v == null || v.isBlank()) ? fallbackSecret : v;
    }

    /** 上一版本（previous）密钥明文，无则 null（用于平滑轮换验签） */
    public String resolvePrevious() {
        return readDecrypted(K_PREV);
    }

    /** 是否仍为出厂默认值（用于前端登录引导） */
    public boolean isDefault() {
        return DEFAULT_SECRET.equals(resolve());
    }

    /** 生成 32 位高强度随机密钥（[A-Za-z0-9]，避免 Base64 长度漂移与特殊字符转义问题） */
    public String generate() {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(GEN_LEN);
        for (int i = 0; i < GEN_LEN; i++) {
            sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 保存密钥：强度校验 → 旧 current 转为 previous（密文）→ 新值写入 current（密文），落库并刷新缓存 */
    public void save(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密钥不能为空");
        }
        if (secret.length() < MIN_LEN) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密钥长度至少 " + MIN_LEN + " 位");
        }
        if (DEFAULT_SECRET.equals(secret)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "禁止使用出厂默认密钥，请使用随机生成或自定义高强度密钥");
        }
        // 1) 旧 current 降级为 previous（密文），供下游在切换窗口继续验签
        String oldCurrent = readDecrypted(K);
        if (oldCurrent != null && !oldCurrent.isBlank() && !oldCurrent.equals(secret)) {
            String encPrev = cipher.encrypt(oldCurrent);
            ConfigCache.put(redisUtil, K_PREV, encPrev, () -> configMapper.upsert(K_PREV, encPrev));
        }
        // 2) 新值写入 current（密文）
        String enc = cipher.encrypt(secret);
        ConfigCache.put(redisUtil, K, enc, () -> configMapper.upsert(K, enc));
        log.info("网关内部签名密钥已轮换（{} 位，密文存储），约 30 秒内热生效，各服务无需重启", secret.length());
    }
}
