package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 终端软件基线策略：认证时要求终端运行指定软件。
 * <p>matchMode=PROCESS 只比对进程名；matchMode=HASH 额外要求可执行文件 SHA256 匹配（防同名伪装）。</p>
 */
@Data
public class SoftwarePolicy {
    private Long id;
    /** 软件显示名（日志/界面展示） */
    private String name;
    /** 进程名（不含路径，可带 .exe，匹配忽略大小写与扩展名） */
    private String processName;
    /** 可执行文件 SHA256（小写 hex，空=仅校验进程名） */
    private String sha256;
    /** PROCESS / HASH */
    private String matchMode;
    /** 1=必须满足 0=仅记录 */
    private Integer required;
    private Integer enabled;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
