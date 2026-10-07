package com.nac.auth.sms;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 腾讯云短信网关。凭证环境变量注入。
 */
@Slf4j
@Component
public class TencentSmsGateway implements SmsGateway {

    @Value("${nac.sms.tencent.secret-id:}")
    private String secretId;
    @Value("${nac.sms.tencent.secret-key:}")
    private String secretKey;
    @Value("${nac.sms.tencent.sms-sdk-app-id:}")
    private String appId;
    @Value("${nac.sms.tencent.sign-name:NAC准入}")
    private String defaultSign;
    @Value("${nac.sms.tencent.template-id:}")
    private String defaultTemplate;
    @Value("${nac.sms.tencent.region:ap-guangzhou}")
    private String region;

    @Override
    public String name() {
        return "tencent";
    }

    @Override
    public boolean send(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        Credential cred = new Credential(secretId, secretKey);
        HttpProfile http = new HttpProfile();
        http.setEndpoint("sms.tencentcloudapi.com");
        ClientProfile profile = new ClientProfile();
        profile.setHttpProfile(http);
        SmsClient client = new SmsClient(cred, region, profile);

        SendSmsRequest req = new SendSmsRequest();
        req.setPhoneNumberSet(new String[]{"+86" + phone});
        req.setSmsSdkAppId(appId);
        req.setSignName(signName != null ? signName : defaultSign);
        req.setTemplateId(templateCode != null ? templateCode : defaultTemplate);
        String code = params.getOrDefault("code", "");
        req.setTemplateParamSet(new String[]{code});

        SendSmsResponse resp = client.SendSms(req);
        return resp.getSendStatusSet() != null && resp.getSendStatusSet().length > 0
                && "Ok".equals(resp.getSendStatusSet()[0].getCode());
    }
}
