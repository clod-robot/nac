package com.nac.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 云 KMS KEK 提供者（Adapter 占位）：KEK 由云厂商 KMS 保管，应用通过 AK/SK 或实例角色调用。
 * 默认不生效（需 nac.security.kek.cloud.enabled=true）。启用后选定厂商在此接入：
 *   - 阿里云/华为云/腾讯云/AWS KMS：用厂商 SDK 的 GenerateDataKey/Decrypt，或经 AbstractHttpKekProvider 走 REST；
 *   - 推荐信封加密：KMS 生成 DEK，密文 DEK 落库，运行时请 KMS 解密 DEK 后在内存使用。
 * 未接入 SDK 时返回 null，链路自动降级，绝不中断现网。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nac.security.kek.cloud.enabled", havingValue = "true")
public class CloudKmsProvider implements KekProvider {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String getKekMaterial() {
        // TODO: 选定云厂商并接入 SDK 后返回真实 KEK/DEK；未接入前返回 null 触发降级
        log.warn("云 KMS KEK 提供者已启用但尚未接入厂商 SDK，降级到下一优先级 KEK 来源");
        return null;
    }
}
