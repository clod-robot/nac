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
}
