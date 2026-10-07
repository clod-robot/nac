package com.nac.auth.dto;

import lombok.Data;

/** 短信网关配置（界面可配，存 Redis，env 兜底）。 */
@Data
public class SmsConfig {
    /** 当前启用的网关：mock / aliyun / tencent / huawei */
    private String provider = "mock";
    private Aliyun aliyun = new Aliyun();
    private Tencent tencent = new Tencent();
    private Huawei huawei = new Huawei();

    @Data
    public static class Aliyun {
        private String accessKeyId;
        private String accessKeySecret;
        private String signName;
        private String templateCode;
    }

    @Data
    public static class Tencent {
        private String secretId;
        private String secretKey;
        private String region;
        private String appId;       // SmsSdkAppId
        private String signName;
        private String templateId;
    }

    @Data
    public static class Huawei {
        private String appKey;
        private String appSecret;
        private String sender;
        private String templateId;
        private String url;
    }
}
