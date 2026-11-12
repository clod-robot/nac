package com.nac.radius.dto;

import lombok.Data;

import java.util.List;

/** 客户端软件基线心跳上报请求（/api/client/software-report）。 */
@Data
public class SoftwareReportReq {
    private String mac;
    private String ip;
    private String hostname;
    private String clientVersion;
    /** 每次上报随机 32 位 hex，防重放 */
    private String nonce;
    /** 客户端时间戳（秒） */
    private Long ts;
    private List<SoftwareItem> software;
    /** HMAC-SHA256(secret, "nonce|mac|ts|canonicalList") */
    private String signature;

    @Data
    public static class SoftwareItem {
        private String name;
        private String path;
        /** 完整 64 位小写 hex */
        private String sha256;
    }
}
