package com.nac.radius.entity;

import lombok.Data;

/**
 * 终端软件基线策略（与 nac-auth 共用 nac 库 sys_software_policy 表，radius 侧只读）。
 */
@Data
public class SoftwarePolicy {
    private Long id;
    private String name;
    private String processName;
    private String sha256;
    private String matchMode;
    /** 1=必须满足 0=仅记录 */
    private Integer required;
    private Integer enabled;
}
