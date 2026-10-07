package com.nac.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具（jjwt 0.12 API）。密钥环境变量注入，禁止硬编码。默认 8h，支持滑动续期。
 */
@Component
public class JwtUtil {

    @Value("${nac.jwt.secret:${JWT_SECRET:NacJwtSecret@2026-Phase1-ChangeMe}}")
    private String secret;

    @Value("${nac.jwt.expire-seconds:28800}")
    private long expireSeconds;

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private SecretKey key() {
        // HS512 需 64 字节密钥，用 SHA-512 从配置派生
        return Keys.hmacShaKeyFor(sha512(secret));
    }

    private byte[] sha512(String data) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 not available", e);
        }
    }

    public String generate(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USERNAME, username);
        claims.put(CLAIM_ROLE, role);
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000L))
                .signWith(key(), Jwts.SIG.HS512)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        Object v = parse(token).get(CLAIM_USER_ID);
        return v == null ? null : Long.valueOf(v.toString());
    }

    public String getUsername(String token) {
        Object v = parse(token).get(CLAIM_USERNAME);
        return v == null ? null : v.toString();
    }

    public String getRole(String token) {
        Object v = parse(token).get(CLAIM_ROLE);
        return v == null ? null : v.toString();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
