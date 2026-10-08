package com.nac.auth.sms;

import com.nac.auth.dto.SmsConfig;
import com.nac.auth.service.SmsConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 自定义 HTTP 短信网关：按界面配置的 URL / 方法 / 请求头 / 报文模板发送，
 * 模板支持 {phone} {code} {signName} {template} 占位符，可对接任意自有短信平台。
 */
@Slf4j
@Component
public class CustomSmsGateway implements SmsGateway {

    private final SmsConfigService configService;

    public CustomSmsGateway(SmsConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String name() {
        return "custom";
    }

    @Override
    public boolean send(String phone, String signName, String templateCode, Map<String, String> params) throws Exception {
        SmsConfig.Custom cfg = configService.get().getCustom();
        String url = cfg.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("自定义网关未配置请求 URL");
        }
        String code = params == null ? "" : params.getOrDefault("code", "");
        String tpl = templateCode == null ? "" : templateCode;
        String sign = signName == null ? "" : signName;

        url = render(url, phone, code, sign, tpl);
        // 仅允许 http/https，降低 SSRF 风险
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalStateException("自定义网关 URL 仅支持 http/https");
        }

        String method = (cfg.getMethod() == null || cfg.getMethod().isBlank()) ? "POST" : cfg.getMethod().trim().toUpperCase();
        String contentType = "json".equalsIgnoreCase(cfg.getContentType()) ? "application/json;charset=UTF-8"
                : "application/x-www-form-urlencoded;charset=UTF-8";

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", contentType);

        // 自定义请求头：每行 "Key: Value"
        if (cfg.getHeaders() != null && !cfg.getHeaders().isBlank()) {
            for (String line : cfg.getHeaders().split("\\R")) {
                line = render(line.trim(), phone, code, sign, tpl);
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String k = line.substring(0, idx).trim();
                    String v = line.substring(idx + 1).trim();
                    if (!k.isEmpty()) b.header(k, v);
                }
            }
        }

        String body = cfg.getBodyTemplate() == null ? "" : render(cfg.getBodyTemplate(), phone, code, sign, tpl);
        if ("GET".equals(method)) {
            b.GET();
        } else {
            b.method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        HttpResponse<String> resp = HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
        boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300;
        if (cfg.getSuccessContains() != null && !cfg.getSuccessContains().isBlank()) {
            ok = resp.body() != null && resp.body().contains(cfg.getSuccessContains());
        }
        if (!ok) {
            log.warn("自定义网关返回非成功 status={} body={}", resp.statusCode(),
                    resp.body() == null ? "" : resp.body().replaceAll("\\s+", " ").substring(0, Math.min(200, resp.body().length())));
        }
        return ok;
    }

    /** 占位符替换：{phone} {code} {signName} {template} */
    private static String render(String tpl, String phone, String code, String signName, String template) {
        if (tpl == null) return "";
        return tpl.replace("{phone}", nz(phone))
                .replace("{code}", nz(code))
                .replace("{signName}", nz(signName))
                .replace("{template}", nz(template));
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
