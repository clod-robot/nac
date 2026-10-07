package com.nac.auth.sms;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.nac.auth.dto.SmsConfig;
import com.nac.auth.service.SmsConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 腾讯云短信网关。凭证优先取界面配置，未配置时回退环境变量。
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

    private final SmsConfigService configService;

    public TencentSmsGateway(SmsConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String name() {
        return "tencent";
    }

    @Override
    public boolean send(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        SmsConfig.Tencent cfg = configService.get().getTencent();
        Credential cred = new Credential(nz(cfg.getSecretId(), secretId), nz(cfg.getSecretKey(), secretKey));
        HttpProfile http = new HttpProfile();
        http.setEndpoint("sms.tencentcloudapi.com");
        ClientProfile profile = new ClientProfile();
        profile.setHttpProfile(http);
        SmsClient client = new SmsClient(cred, nz(cfg.getRegion(), region), profile);

        SendSmsRequest req = new SendSmsRequest();
        req.setPhoneNumberSet(new String[]{"+86" + phone});
        req.setSmsSdkAppId(nz(cfg.getAppId(), appId));
        req.setSignName(nz(signName, nz(cfg.getSignName(), defaultSign)));
        req.setTemplateId(nz(templateCode, nz(cfg.getTemplateId(), defaultTemplate)));
        String code = params.getOrDefault("code", "");
        req.setTemplateParamSet(new String[]{code});

        SendSmsResponse resp = client.SendSms(req);
        return resp.getSendStatusSet() != null && resp.getSendStatusSet().length > 0
                && "Ok".equals(resp.getSendStatusSet()[0].getCode());
    }

    private static String nz(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
