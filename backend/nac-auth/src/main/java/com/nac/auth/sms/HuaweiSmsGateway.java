package com.nac.auth.sms;

import com.nac.auth.dto.SmsConfig;
import com.nac.auth.service.SmsConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 华为云短信网关：HTTP 调用 + WSSE UsernameToken 签名。凭证优先取界面配置，未配置时回退环境变量。
 */
@Slf4j
@Component
public class HuaweiSmsGateway implements SmsGateway {

    @Value("${nac.sms.huawei.app-key:}")
    private String appKey;
    @Value("${nac.sms.huawei.app-secret:}")
    private String appSecret;
    @Value("${nac.sms.huawei.sender:}")
    private String sender;
    @Value("${nac.sms.huawei.template-id:}")
    private String defaultTemplate;
    @Value("${nac.sms.huawei.url:https://smsapi.cn-north-4.myhuaweicloud.com:443/sms/batchSendSms/v1}")
    private String url;

    private final SmsConfigService configService;

    public HuaweiSmsGateway(SmsConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String name() {
        return "huawei";
    }

    @Override
    public boolean send(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        SmsConfig.Huawei cfg = configService.get().getHuawei();
        String effKey = nz(cfg.getAppKey(), appKey);
        String effSecret = nz(cfg.getAppSecret(), appSecret);
        String effSender = nz(cfg.getSender(), sender);
        String effUrl = nz(cfg.getUrl(), url);
        String effTpl = nz(templateCode, nz(cfg.getTemplateId(), defaultTemplate));

        String created = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String passwordDigest = base64(sha256((nonce + created + effSecret).getBytes(StandardCharsets.UTF_8)));
        String wsse = String.format("UsernameToken Username=\"%s\",PasswordDigest=\"%s\",Nonce=\"%s\",Created=\"%s\"",
                effKey, passwordDigest, nonce, created);

        String body = "from=" + effSender
                + "&to=" + phone
                + "&templateId=" + effTpl
                + "&templateParas=" + "[\"" + params.getOrDefault("code", "") + "\"]";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(effUrl))
                .header("Authorization", "WSSE realm=\"SDP\",profile=\"UsernameToken\",type=\"Appkey\"")
                .header("X-WSSE", wsse)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 200 && resp.body().contains("\"code\":\"0\"");
    }

    private static String nz(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    private byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
