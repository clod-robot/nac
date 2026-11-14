package com.nac.radius.entity;

import lombok.Data;

/** 隔离区/修复区下发配置（只读，单行 id=1）。 */
@Data
public class IsolationConfig {
    private Long id;
    private Integer enabled;
    private String mode;
    private String quarantineAcl;
    private String remediationAcl;
    private Integer quarantineVlan;
    private Integer remediationVlan;
    private String remediationPortalUrl;
    private String remediationServerIps;
    private Integer autoRelease;
    private Integer releaseHoldMinutes;
}
