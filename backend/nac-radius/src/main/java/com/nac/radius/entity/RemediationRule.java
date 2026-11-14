package com.nac.radius.entity;

import lombok.Data;

/** 修复规则（只读）。 */
@Data
public class RemediationRule {
    private Long id;
    private String name;
    private String triggerType;
    private String triggerValue;
    private String actionType;
    private Long targetId;
    private String actionUrl;
    private Integer enabled;
}
