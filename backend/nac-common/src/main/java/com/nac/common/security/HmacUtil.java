package com.nac.common.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 工具：网关对下游请求签名 / 下游验签，防直连与篡改。
 */
@Component
public class HmacUtil {

    private static final String ALG = "HmacSHA256";

    /** 签名：sign(secret, ts, method, path, nonce) → 小写 hex；nonce 参与签名防重放 */
    public String sign(String secret, String ts, String method, String path, String nonce) {
        String payload = ts + "\n" + method + "\n" + path + "\n" + (nonce == null ? "" : nonce);
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALG));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    /** 兼容旧调用：无 nonce */
    public String sign(String secret, String ts, String method, String path) {
        return sign(secret, ts, method, path, "");
    }

    /** 验签（含 nonce）：恒定时间比较，防时序攻击；时间窗 ±300s */
    public boolean verify(String secret, String ts, String method, String path, String nonce, String sign) {
        if (sign == null || ts == null) return false;
        long tsLong;
        try {
            tsLong = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() / 1000 - tsLong) > 300) {
            return false;
        }
        return constantTimeEquals(sign(secret, ts, method, path, nonce), sign);
    }

    public boolean verify(String secret, String ts, String method, String path, String sign) {
        return verify(secret, ts, method, path, "", sign);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
