package com.nac.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 手机号加密 + 脱敏门面。库内 AES-256-GCM 密文存储，展示脱敏（前3后4）。
 */
@Component
public class PhoneCryptoUtil {

    @Autowired
    private AesCryptoUtil aesCryptoUtil;

    /** 加密手机号入库 */
    public String encrypt(String phone) {
        return aesCryptoUtil.encrypt(phone);
    }

    /** 解密（仅授权场景，如 admin 且 phoneAuditEnabled） */
    public String decrypt(String cipher) {
        return aesCryptoUtil.decrypt(cipher);
    }

    /** 脱敏展示 */
    public static String mask(String phone) {
        return SensitiveUtil.maskPhone(phone);
    }
}
