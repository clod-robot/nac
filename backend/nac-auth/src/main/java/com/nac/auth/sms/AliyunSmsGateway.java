package com.nac.auth.sms;

import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.nac.auth.dto.SmsConfig;
import com.nac.auth.service.SmsConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 阿里云短信网关，支持两类服务（界面按 Tab 切换）：
 *  - 短信验证码（mode=code）：dysmsapi「短信服务」，本系统生成验证码，按签名 + 模板下发；
 *  - 短信认证服务（mode=auth）：dypnsapi「号码认证服务」，阿里云托管验证码的生成与下发，无需签名/模板。
 * 凭证优先取界面配置，未配置时回退环境变量。
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
    @Value("${nac.sms.aliyun.mode:code}")
    private String defaultMode;
    /** 短信认证服务（auth）兜底：使用阿里云「系统赠送」签名，须搭配赠送模板。 */
    @Value("${nac.sms.aliyun.auth-sign-name:恒创联众}")
    private String authSignName;
    @Value("${nac.sms.aliyun.auth-template-code:100001}")
    private String authTemplateCode;

    private final SmsConfigService configService;

    public AliyunSmsGateway(SmsConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String name() {
        return "aliyun";
    }

    /** 短信验证码模式：沿用本系统生成的验证码，按签名 + 模板下发。 */
    @Override
    public boolean send(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        SmsConfig.Aliyun cfg = configService.get().getAliyun();
        Config config = new Config()
                .setAccessKeyId(nz(cfg.getAccessKeyId(), ak))
                .setAccessKeySecret(nz(cfg.getAccessKeySecret(), sk))
                .setEndpoint("dysmsapi.aliyuncs.com");
        com.aliyun.dysmsapi20170525.Client client = new com.aliyun.dysmsapi20170525.Client(config);
        SendSmsRequest req = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(nz(signName, nz(cfg.getSignName(), defaultSign)))
                .setTemplateCode(nz(templateCode, nz(cfg.getTemplateCode(), defaultTemplate)))
                .setTemplateParam(JSON.toJSONString(params));
        String code = client.sendSms(req).getBody().getCode();
        return "OK".equals(code);
    }

    /**
     * 短信认证服务模式：由阿里云生成验证码并下发（ReturnVerifyCode=true），返回云端验证码供本地校验；
     * 短信验证码模式回退默认实现（返回本系统生成的 code）。
     */
    @Override
    public String sendAndGetCode(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        SmsConfig.Aliyun cfg = configService.get().getAliyun();
        if (!"auth".equalsIgnoreCase(nz(cfg.getMode(), defaultMode))) {
            return SmsGateway.super.sendAndGetCode(phone, signName, templateCode, params);
        }
        Config config = new Config()
                .setAccessKeyId(nz(cfg.getAccessKeyId(), ak))
                .setAccessKeySecret(nz(cfg.getAccessKeySecret(), sk))
                .setEndpoint("dypnsapi.aliyuncs.com");
        com.aliyun.dypnsapi20170525.Client client = new com.aliyun.dypnsapi20170525.Client(config);
        // TemplateParam 必填：以 "##code##" 占位符交由阿里云按 CodeType 规则生成验证码（CodeType=1 纯数字），
        // ReturnVerifyCode=true 时云端会把生成的验证码回传，供本地 Redis 校验。
        com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest req =
                new com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest()
                        .setPhoneNumber(phone)
                        .setReturnVerifyCode(true)
                        .setTemplateParam("{\"code\":\"##code##\"}")
                        .setCodeType(1L)
                        .setCodeLength(cfg.getCodeLength() == null ? 6L : cfg.getCodeLength())
                        .setValidTime(cfg.getValidTime() == null ? 300L : cfg.getValidTime());
        if (cfg.getSchemeName() != null && !cfg.getSchemeName().isBlank()) req.setSchemeName(cfg.getSchemeName());
        // SignName/TemplateCode 均为必填：优先界面配置的赠送签名/模板，留空时回退服务端默认（恒创联众 / 100001）
        req.setSignName(nz(cfg.getAuthSignName(), authSignName));
        req.setTemplateCode(nz(cfg.getAuthTemplateCode(), authTemplateCode));
        com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse resp = client.sendSmsVerifyCode(req);
        var body = resp.getBody();
        if (body != null && "OK".equals(body.getCode()) && body.getModel() != null) {
            return body.getModel().getVerifyCode();
        }
        log.warn("阿里云短信认证服务下发失败 code={} msg={}",
                body == null ? null : body.getCode(), body == null ? null : body.getMessage());
        return null;
    }

    private static String nz(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
