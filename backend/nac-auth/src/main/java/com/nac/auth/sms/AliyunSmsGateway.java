package com.nac.auth.sms;

import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.nac.auth.dto.SmsConfig;
import com.nac.auth.service.SmsConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 阿里云短信网关。凭证优先取界面配置，未配置时回退环境变量。
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

    private final SmsConfigService configService;

    public AliyunSmsGateway(SmsConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String name() {
        return "aliyun";
    }

    @Override
    public boolean send(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        SmsConfig.Aliyun cfg = configService.get().getAliyun();
        String effAk = nz(cfg.getAccessKeyId(), ak);
        String effSk = nz(cfg.getAccessKeySecret(), sk);
        Config config = new Config()
                .setAccessKeyId(effAk)
                .setAccessKeySecret(effSk)
                .setEndpoint("dysmsapi.aliyuncs.com");
        Client client = new Client(config);
        SendSmsRequest req = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(nz(signName, nz(cfg.getSignName(), defaultSign)))
                .setTemplateCode(nz(templateCode, nz(cfg.getTemplateCode(), defaultTemplate)))
                .setTemplateParam(JSON.toJSONString(params));
        String code = client.sendSms(req).getBody().getCode();
        return "OK".equals(code);
    }

    private static String nz(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
