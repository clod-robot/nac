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
    /** 可选签名：nonce|mac|ts|taskId|status 的 HMAC-SHA256；缺省时兼容旧客户端不校验。 */
    private String nonce;
    private Long ts;
    private String signature;
}
