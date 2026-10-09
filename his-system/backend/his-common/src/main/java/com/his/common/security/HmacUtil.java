package com.his.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 网关内部通信签名工具：HMAC-SHA256(ts + method + path)，
 * 下游服务校验签名，阻止直连端口访问（参考 NAC 项目 GatewayTrustFilter 设计）。
 */
public class HmacUtil {

    private HmacUtil() {}

    public static String sign(String secret, String ts, String method, String path) {
        try {
            String data = ts + method.toUpperCase() + path;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC 签名失败", e);
        }
    }

    public static boolean verify(String secret, String ts, String method, String path, String signature) {
        if (signature == null) return false;
        try {
            long t = Long.parseLong(ts);
            // ±300 秒时间窗，防重放
            if (Math.abs(System.currentTimeMillis() / 1000 - t) > 300) return false;
        } catch (NumberFormatException e) {
            return false;
        }
        return sign(secret, ts, method, path).equals(signature);
    }
}
