package com.nac.radius.entity;

import lombok.Data;

/**
 * RADIUS 认证使用的用户视图。仅取认证必要字段，最小化数据暴露。
 * radiusPasswordCipher 为 AES-256-GCM 可逆密文（CHAP/PAP 校验需要明文）。
 */
@Data
public class RadiusUser {
    private Long id;
    private String username;
    private String passwordHash;       // BCrypt（PAP 兜底校验用）
    private String radiusPasswordCipher; // AES-256-GCM 可逆密文，RADIUS 专用
    private Integer status;            // 1启用 0禁用
}
