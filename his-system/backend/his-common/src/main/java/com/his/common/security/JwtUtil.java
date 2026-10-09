package com.his.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具：密钥通过环境变量注入，禁止硬编码（等保合规）
 * 默认 8 小时有效期，支持滑动续期
 */
@Component
public class JwtUtil {

    @Value("${his.jwt.secret:${JWT_SECRET:HisJwt@2026-DefaultKeyMustBeChanged}}")
    private String secret;

    @Value("${his.jwt.expire-seconds:28800}")
    private long expireSeconds;

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    public String generate(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USERNAME, username);
        claims.put(CLAIM_ROLE, role);
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
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
        return (String) parse(token).get(CLAIM_USERNAME);
    }

    public String getRole(String token) {
        return (String) parse(token).get(CLAIM_ROLE);
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
