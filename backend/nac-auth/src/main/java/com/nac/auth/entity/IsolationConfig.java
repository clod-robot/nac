package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 隔离区/修复区下发配置（sys_isolation_config，单行 id=1）。 */
@Data
public class IsolationConfig {
    private Long id;
    /** 1=校验未过进入隔离区（不再直接拒绝认证） */
    private Integer enabled;
    /** ACL=Filter-Id 下发 / VLAN=隔离 VLAN 下发 */
    private String mode;
    private String quarantineAcl;
    private String remediationAcl;
    private Integer quarantineVlan;
    private Integer remediationVlan;
    private String remediationPortalUrl;
    private String remediationServerIps;
    /** 1=合规后自动释放 */
    private Integer autoRelease;
    /** 自动释放前最短隔离时长（分钟） */
    private Integer releaseHoldMinutes;
    private LocalDateTime updateTime;
}
