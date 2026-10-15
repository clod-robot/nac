package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 在线会话（对应 sys_online_session）：Portal 网页认证成功后写入，供在线终端展示。 */
@Data
public class OnlineSession {
    private Long id;
    private String acctSessionId;
    private String username;
    private String usernameMask;
    private String mac;
    private String nasIp;
    private String framedIp;
    private Integer vlanId;
    /** 无线 SSID（来自 Portal URL 参数） */
    private String ssid;
    /** NAS/AC 设备名称（来自 Portal URL 参数） */
    private String nasName;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime updateTime;
}
