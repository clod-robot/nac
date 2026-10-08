package com.nac.auth.service;

import com.nac.auth.dto.LoginVO;
import com.nac.auth.entity.SmsRecord;
import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.SmsRecordMapper;
import com.nac.auth.mapper.SysUserMapper;
import com.nac.auth.sms.SmsGateway;
import com.nac.auth.sms.SmsGatewayFactory;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.ResultCode;
import com.nac.common.security.JwtUtil;
import com.nac.common.security.PhoneCryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 短信验证码服务：图形验证码前置、60s 冷却、每日限额、失败失效；
 * 手机号全程不落明文（AES 加密入库 + HMAC 盲索引查询）。
 */
@Slf4j
@Service
public class SmsService {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private final SmsGatewayFactory gatewayFactory;
    private final SmsConfigService configService;
    private final CaptchaService captchaService;
    private final RedisUtil redisUtil;
    private final PhoneCryptoUtil phoneCryptoUtil;
    private final JwtUtil jwtUtil;
    private final SysUserMapper userMapper;
    private final SmsRecordMapper smsRecordMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${nac.sms.provider:mock}")
    private String provider;
    @Value("${nac.crypto.phone-password:NcePhone@2026}")
    private String blindSecret;
    @Value("${nac.sms.code-ttl:300}")
    private int codeTtl;
    @Value("${nac.sms.cooldown:60}")
    private int cooldown;
    @Value("${nac.sms.daily-limit:10}")
    private int dailyLimit;
    @Value("${nac.sms.max-fail:5}")
    private int maxFail;

    public SmsService(SmsGatewayFactory gatewayFactory, SmsConfigService configService, CaptchaService captchaService,
                      RedisUtil redisUtil, PhoneCryptoUtil phoneCryptoUtil, JwtUtil jwtUtil, SysUserMapper userMapper,
                      SmsRecordMapper smsRecordMapper, PasswordEncoder passwordEncoder) {
        this.gatewayFactory = gatewayFactory;
        this.configService = configService;
        this.captchaService = captchaService;
        this.redisUtil = redisUtil;
        this.phoneCryptoUtil = phoneCryptoUtil;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.smsRecordMapper = smsRecordMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** HMAC-SHA256 盲索引：不可逆，仅用于等值查询与限流 key */
    public String blindIndex(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(blindSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("盲索引计算失败", e);
        }
    }

    public boolean sendCode(String phone, String captchaKey, String captchaCode, String bizType, String ip) {
        if (!PHONE.matcher(phone).matches()) {
            throw new BusinessException(ResultCode.PHONE_FORMAT_ERROR);
        }
        if (!captchaService.verify(captchaKey, captchaCode)) {
            throw new BusinessException(ResultCode.CAPTCHA_INVALID);
        }
        String blind = blindIndex(phone);
        if (redisUtil.hasKey(RedisKeyConstants.SMS_COOLDOWN_PREFIX + blind)) {
            throw new BusinessException(ResultCode.SMS_COOLDOWN);
        }
        String dailyKey = RedisKeyConstants.SMS_DAILY_COUNT_PREFIX + LocalDate.now() + ":" + blind;
        String cnt = redisUtil.get(dailyKey);
        if (cnt != null && Integer.parseInt(cnt) >= dailyLimit) {
            throw new BusinessException(ResultCode.SMS_DAILY_LIMIT);
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        boolean ok = false;
        String failReason = null;
        SmsGateway gateway = gatewayFactory.get(configService.get().getProvider());
        try {
            ok = gateway.send(phone, null, null, Map.of("code", code));
        } catch (Exception e) {
            failReason = e.getMessage();
            log.warn("短信网关发送失败 gateway={} phoneBlind={} err={}", gateway.name(), blind, e.getMessage());
        }
        if (ok) {
            redisUtil.set(RedisKeyConstants.SMS_CODE_PREFIX + blind, code, codeTtl);
            redisUtil.set(RedisKeyConstants.SMS_COOLDOWN_PREFIX + blind, "1", cooldown);
            redisUtil.incr(dailyKey, 86400);
        }
        SmsRecord rec = new SmsRecord();
        rec.setPhoneCipher(phoneCryptoUtil.encrypt(phone));
        rec.setPhoneBlind(blind);
        rec.setBizType(bizType);
        rec.setProvider(gateway.name());
        rec.setStatus(ok ? 1 : 0);
        rec.setErrorMsg(failReason);
        rec.setIp(ip);
        try {
            smsRecordMapper.insert(rec);
        } catch (Exception e) {
            log.warn("短信记录落库失败: {}", e.getMessage());
        }
        if (!ok) {
            throw new BusinessException(ResultCode.SMS_GATEWAY_ERROR);
        }
        return true;
    }

    public boolean verifyCode(String phone, String code) {
        String blind = blindIndex(phone);
        String stored = redisUtil.get(RedisKeyConstants.SMS_CODE_PREFIX + blind);
        if (stored == null) {
            throw new BusinessException(ResultCode.SMS_CODE_INVALID);
        }
        if (!stored.equals(code)) {
            long fail = redisUtil.incr(RedisKeyConstants.SMS_FAIL_PREFIX + blind, codeTtl);
            if (fail >= maxFail) {
                redisUtil.delete(RedisKeyConstants.SMS_CODE_PREFIX + blind);
            }
            throw new BusinessException(ResultCode.SMS_CODE_INVALID);
        }
        redisUtil.delete(RedisKeyConstants.SMS_CODE_PREFIX + blind);
        redisUtil.delete(RedisKeyConstants.SMS_FAIL_PREFIX + blind);
        return true;
    }

    /** 短信验证码登录：校验通过后按手机号匹配用户，不存在则自动创建普通用户 */
    public LoginVO loginBySms(String phone, String code, String ip) {
        verifyCode(phone, code);
        String blind = blindIndex(phone);
        SysUser user = userMapper.selectByPhoneBlind(blind);
        if (user == null) {
            SysUser u = new SysUser();
            u.setUsername("u_" + blind.substring(0, 16));
            u.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            u.setRealName("手机用户");
            u.setPhoneCipher(phoneCryptoUtil.encrypt(phone));
            u.setPhoneBlind(blind);
            u.setRoleCode("user");
            u.setStatus(1);
            userMapper.insert(u);
            user = userMapper.selectByPhoneBlind(blind);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRoleCode());
        redisUtil.set(RedisKeyConstants.TOKEN_PREFIX + user.getId(), token, (int) jwtUtil.getExpireSeconds());
        log.info("短信登录成功: userId={} ip={}", user.getId(), ip);
        boolean mustChangePwd = passwordEncoder.matches("admin123", user.getPasswordHash());
        return new LoginVO(token, user.getUsername(), user.getRoleCode(), user.getId(), mustChangePwd);
    }
}
