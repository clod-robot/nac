package com.nac.auth.sms;

import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 阿里云短信网关。凭证环境变量注入，禁止硬编码。
 */
@Slf4j
@Component
public class AliyunSmsGateway implements SmsGateway {

    @Value("${nac.sms.aliyun.access-key-id:}")
    private String ak;
    @Value("${nac.sms.aliyun.access-key-secret:}")
    private String sk;
    @Value("${nac.sms.aliyun.sign-name:NAC准入}")
    private String defaultSign;
    @Value("${nac.sms.aliyun.template-code:}")
    private String defaultTemplate;

    @Override
    public String name() {
        return "aliyun";
    }

    @Override
    public boolean send(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        Config config = new Config()
                .setAccessKeyId(ak)
                .setAccessKeySecret(sk)
                .setEndpoint("dysmsapi.aliyuncs.com");
        Client client = new Client(config);
        SendSmsRequest req = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName != null ? signName : defaultSign)
                .setTemplateCode(templateCode != null ? templateCode : defaultTemplate)
                .setTemplateParam(JSON.toJSONString(params));
        String code = client.sendSms(req).getBody().getCode();
        return "OK".equals(code);
    }
}
