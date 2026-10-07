package com.nac.auth.sms;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信网关工厂：按配置 nac.sms.provider 选择实现，未命中降级 mock。
 */
@Component
public class SmsGatewayFactory {

    private final Map<String, SmsGateway> gatewayMap = new HashMap<>();

    public SmsGatewayFactory(List<SmsGateway> gateways) {
        for (SmsGateway g : gateways) {
            gatewayMap.put(g.name(), g);
        }
    }

    public SmsGateway get(String name) {
        SmsGateway g = gatewayMap.get(name);
        return g != null ? g : gatewayMap.get("mock");
    }
}
