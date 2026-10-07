package com.nac.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.redis.RedisUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 图形验证码：生成（存 Redis 300s）+ 校验后销毁（防重放）。
 */
@Slf4j
@Service
public class CaptchaService {

    private static final int TTL = 300;

    private final RedisUtil redisUtil;

    public CaptchaService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    public CaptchaVO generate() {
        System.setProperty("java.awt.headless", "true");
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 6);
        String key = IdUtil.fastSimpleUUID();
        redisUtil.set(RedisKeyConstants.CAPTCHA_PREFIX + key, captcha.getCode(), TTL);
        return new CaptchaVO(key, captcha.getImageBase64Data());
    }

    /** 校验成功返回 true 并销毁；失败返回 false（不销毁，允许有限重试由调用方控制） */
    public boolean verify(String key, String code) {
        if (key == null || code == null) return false;
        String stored = redisUtil.get(RedisKeyConstants.CAPTCHA_PREFIX + key);
        if (stored == null) return false;
        boolean ok = stored.equalsIgnoreCase(code);
        if (ok) {
            redisUtil.delete(RedisKeyConstants.CAPTCHA_PREFIX + key);
        }
        return ok;
    }

    @Data
    public static class CaptchaVO {
        private final String captchaKey;
        private final String captchaImg;
    }
}
