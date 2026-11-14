package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 修复任务（sys_remediation_task）。 */
@Data
public class RemediationTask {
    private Long id;
    private Long recordId;
    private String mac;
    private Long ruleId;
    /** PATCH / AV / URL */
    private String libType;
    private Long libId;
    private String title;
    private String actionType;
    private String fileUrl;
    private String installCmd;
    /** PENDING / RUNNING / DONE / FAILED */
    private String status;
    private String resultMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime finishTime;
}
