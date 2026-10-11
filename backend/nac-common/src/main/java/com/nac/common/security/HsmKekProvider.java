package com.nac.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 硬件密码机/HSM KEK 提供者（Adapter 占位）：KEK 在密码机内生成、永不导出，加解密理想形态为机内运算。
 * 默认不生效（需 nac.security.kek.hsm.enabled=true）。启用后请在此接入厂商 SDK / PKCS#11：
 *   - 取到厂商库(.so)路径、登录 PIN 后，通过 SunPKCS11 或厂商 JCE Provider 获取 Key；
 *   - getKekMaterial() 应返回可用于本地派生的材料，或改为把 AES 加解密整体下沉到机内（更合规）。
 * 当前未接入 SDK 时返回 null，链路自动降级到 env/file/vault，绝不中断现网。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nac.security.kek.hsm.enabled", havingValue = "true")
public class HsmKekProvider implements KekProvider {

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public String getKekMaterial() {
        // TODO: 接入密码机 SDK / PKCS#11 后返回真实 KEK 材料；未接入前返回 null 触发降级
        log.warn("HSM KEK 提供者已启用但尚未接入厂商 SDK/PKCS#11，降级到下一优先级 KEK 来源");
        return null;
    }
}
