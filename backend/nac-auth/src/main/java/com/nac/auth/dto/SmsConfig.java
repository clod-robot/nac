package com.nac.auth.dto;

import lombok.Data;

/** 短信网关配置（界面可配，存 Redis，env 兜底）。 */
@Data
public class SmsConfig {
    /** 当前启用的网关：mock / aliyun / tencent / huawei / custom */
    private String provider = "mock";
    private Aliyun aliyun = new Aliyun();
    private Tencent tencent = new Tencent();
    private Huawei huawei = new Huawei();
    private Custom custom = new Custom();

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

    /** 自定义 HTTP 网关：模板化请求，对接任意自有短信平台。 */
    @Data
    public static class Custom {
        /** 请求地址，支持 {phone}/{code} 占位符 */
        private String url;
        /** 请求方法：GET / POST */
        private String method = "POST";
        /** 报文类型：json / form */
        private String contentType = "json";
        /** 请求头，每行 "Key: Value"，支持占位符 */
        private String headers;
        /** 请求体模板，支持 {phone}/{code}/{signName}/{template} 占位符 */
        private String bodyTemplate;
        /** 成功判定：响应体需包含的子串（留空则以 HTTP 2xx 为成功） */
        private String successContains;
    }
}
