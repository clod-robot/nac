package com.nac.log.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** 认证日志（查询视图），对应 sys_auth_log。 */
@Data
public class AuthLog {
    private Long id;
    private String authType;
    private String username;
    private String phone;
    private String mac;
    private String ip;
    private String nasIp;
    private Integer result;
    private String message;
    private LocalDateTime createTime;
}
