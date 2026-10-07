package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信发送记录。手机号仅存密文与盲索引，不存明文、不存验证码。
 */
@Data
public class SmsRecord {
    private Long id;
    private String phoneCipher;
    private String phoneBlind;
    private String bizType;
    private String provider;
    private Integer status;
    private String errorMsg;
    private String ip;
    private LocalDateTime createTime;
}
