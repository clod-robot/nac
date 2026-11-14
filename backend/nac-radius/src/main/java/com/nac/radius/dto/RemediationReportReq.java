package com.nac.radius.dto;

import lombok.Data;

/** 终端上报修复任务执行结果。 */
@Data
public class RemediationReportReq {
    private String mac;
    private Long taskId;
    /** PENDING / RUNNING / DONE / FAILED */
    private String status;
    private String resultMsg;
}
