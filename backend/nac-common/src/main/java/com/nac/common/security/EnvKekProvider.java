package com.nac.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 环境变量 KEK 提供者（默认兜底）。priority=0，作为最低优先级降级项。
 * 配置项：nac.security.kek 或环境变量 NAC_KEK。
 */
@Component
public class EnvKekProvider implements KekProvider {

    @Value("${nac.security.kek:${NAC_KEK:}}")
    private String kek;

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public String getKekMaterial() {
        return (kek == null || kek.isBlank()) ? null : kek;
    }
}
