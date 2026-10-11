package com.nac.common.security;

/**
 * 主密钥(KEK)来源提供者，可插拔：env / Vault / 硬件密码机 / 云 KMS 各自实现。
 * SecretCipher 按 priority 降序选择首个可用 provider，全部不可用时降级到内置默认 KEK 并告警。
 * 新增形态只需实现本接口并注册为 Spring Bean，无需改动加解密调用方。
 */
public interface KekProvider {

    /** 优先级，越大越优先被选中（env 兜底为 0，KMS/HSM 应给更高值） */
    int priority();

    /** 返回主密钥材料；不可用（未配置/连接失败）返回 null，触发降级到下一优先级 */
    String getKekMaterial();
}
