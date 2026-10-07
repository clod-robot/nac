package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理用户实体。手机号以密文存储（phoneCipher），不存明文。
 */
@Data
public class SysUser {
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String phoneCipher;
    private String phoneBlind;
    private String radiusPasswordCipher; // RADIUS 专用可逆密文（内部使用，接口不返回）
    private String roleCode;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
