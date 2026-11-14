package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 终端隔离/修复区记录（sys_isolation_record）。 */
@Data
public class IsolationRecord {
    private Long id;
    private String mac;
    private String ip;
    private String username;
    private String nasIp;
    /** QUARANTINE / REMEDIATION */
    private String zone;
    /** ACTIVE / RELEASED */
    private String status;
    private String reason;
    private String missingJson;
    private String channelType;
    private String channelValue;
    private LocalDateTime enterTime;
    private LocalDateTime lastCheckTime;
    private LocalDateTime releaseTime;
    private String releaseCause;
}
