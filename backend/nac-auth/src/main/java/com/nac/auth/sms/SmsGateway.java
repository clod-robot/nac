package com.nac.auth.sms;

import java.util.Map;

/**
 * 短信网关抽象：可切换阿里云/腾讯云/华为云/Mock。凭证全部环境变量注入。
 */
public interface SmsGateway {
    /** 网关标识：aliyun / tencent / huawei / mock */
    String name();

    /**
     * 发送短信。
     * @param phone        手机号（明文，仅内存中使用，不入库不写日志）
     * @param signName     短信签名
     * @param templateCode 模板编码/ID
     * @param params       模板参数（如 code）
     * @return 是否受理成功
     */
    boolean send(String phone, String signName, String templateCode, Map<String, String> params) throws Exception;

    /**
     * 发送短信并返回「实际生效的验证码」。
     * 默认实现：沿用传入的 code（本地生成），成功返回该 code，失败返回 null。
     * 阿里云「短信认证服务」由云端生成验证码，网关覆写本方法返回云端下发码。
     */
    default String sendAndGetCode(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        boolean ok = send(phone, signName, templateCode, params);
        return ok ? (params == null ? null : params.get("code")) : null;
    }
}
