package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 修复规则（sys_remediation_rule）：缺失项 -> 修复动作。 */
@Data
public class RemediationRule {
    private Long id;
    private String name;
    /** SOFTWARE_MISSING / AV_OUTDATED / PATCH_MISSING */
    private String triggerType;
    private String triggerValue;
    /** INSTALL_PATCH / UPDATE_AV / OPEN_URL */
    private String actionType;
    private Long targetId;
    private String actionUrl;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
