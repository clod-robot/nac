package com.nac.auth.sms;

import com.nac.common.security.SensitiveUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mock 短信网关：不真实发送，仅日志输出（脱敏），用于本地闭环与未配置凭证时降级。
 */
@Slf4j
@Component
public class MockSmsGateway implements SmsGateway {
    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean send(String phone, String signName, String templateCode, Map<String, String> params) {
        log.info("[MOCK SMS] 发送成功 phone={} template={} params={}",
                SensitiveUtil.maskPhone(phone), templateCode, params);
        return true;
    }
}
