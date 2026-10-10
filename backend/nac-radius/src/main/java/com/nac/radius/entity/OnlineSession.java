package com.nac.radius.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** 在线会话（计费驱动，对应 sys_online_session）。 */
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
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime updateTime;
}
