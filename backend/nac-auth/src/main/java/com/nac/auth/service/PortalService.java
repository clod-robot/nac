package com.nac.auth.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.nac.auth.entity.AuthLog;
import com.nac.auth.entity.PortalConfig;
import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.AuthLogMapper;
import com.nac.auth.mapper.PortalConfigMapper;
import com.nac.auth.mapper.SysUserMapper;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.ResultCode;
import com.nac.common.security.SensitiveUtil;
import com.nac.common.syslog.SyslogForwarder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal 认证服务：匿名读取配置；短信验证码通过后颁发 portalToken（存 Redis 8h）。
 */
@Slf4j
@Service
public class PortalService {

    private static final String PORTAL_TOKEN_PREFIX = "nac:portal:token:";
    private static final int TTL = 28800;

    private final PortalConfigMapper configMapper;
    private final AuthLogMapper authLogMapper;
    private final SysUserMapper sysUserMapper;
    private final SmsService smsService;
    private final RedisUtil redisUtil;
    private final ExemptTerminalService exemptService;
    private final SyslogForwarder syslogForwarder;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public PortalService(PortalConfigMapper configMapper, AuthLogMapper authLogMapper, SysUserMapper sysUserMapper,
                         SmsService smsService, RedisUtil redisUtil, ExemptTerminalService exemptService,
                         SyslogForwarder syslogForwarder, PasswordEncoder passwordEncoder,
                         LoginAttemptService loginAttemptService) {
        this.configMapper = configMapper;
        this.authLogMapper = authLogMapper;
        this.sysUserMapper = sysUserMapper;
        this.smsService = smsService;
        this.redisUtil = redisUtil;
        this.exemptService = exemptService;
        this.syslogForwarder = syslogForwarder;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    public Map<String, String> config() {
        Map<String, String> map = new HashMap<>();
        try {
            for (PortalConfig c : configMapper.selectAll()) {
                // 安全：共享密码等敏感项不向匿名（终端）公开接口返回
                if (c.getConfigKey() != null && c.getConfigKey().toLowerCase().contains("secret")) continue;
                map.put(c.getConfigKey(), c.getConfigValue());
            }
        } catch (Exception e) {
            log.warn("Portal 配置读取失败: {}", e.getMessage());
        }
        return map;
    }

    /** 防伪推验签：未开启则放行；开启后需 ts/mac/sign 合法（sign=HMAC-SHA256(密码,"ts|mac")，±300s）。 */
    public boolean verifySign(String ts, String mac, String sign) {
        if (!"true".equals(getCfg("antiForgeryEnabled"))) return true;
        String secret = getCfg("antiForgerySecret");
        if (secret == null || secret.isBlank()) return false;   // 已开启但未设密码 → 默认拒绝，防误开放
        if (ts == null || sign == null) return false;
        long t;
        try { t = Long.parseLong(ts.trim()); } catch (Exception e) { return false; }
        if (Math.abs(System.currentTimeMillis() / 1000 - t) > 300) return false;
        String payload = ts.trim() + "|" + (mac == null ? "" : mac.trim());
        return constantTimeEquals(hmacSha256Hex(secret, payload), sign.trim().toLowerCase());
    }

    private String getCfg(String key) {
        for (PortalConfig c : configMapper.selectAll()) {
            if (key.equals(c.getConfigKey())) return c.getConfigValue();
        }
        return null;
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    public List<PortalConfig> adminConfig() {
        return configMapper.selectAll();
    }

    public void updateConfig(Map<String, String> kv) {
        for (Map.Entry<String, String> e : kv.entrySet()) {
            configMapper.upsert(e.getKey(), e.getValue(), "admin");
        }
    }

    /** 短信验证码通过后颁发 portalToken，记录上网 IP/MAC（脱敏） */
    public String auth(String phone, String code, String mac, String ip) {
        // 免认证终端：MAC/IP 命中白名单直接放行，无需短信验证码
        if (exemptService.isExempt(mac, ip)) {
            String token = IdUtil.fastSimpleUUID();
            Map<String, Object> payload = new HashMap<>();
            payload.put("mac", mac);
            payload.put("ip", ip);
            payload.put("phoneMask", "免认证终端");
            payload.put("ts", System.currentTimeMillis());
            redisUtil.set(PORTAL_TOKEN_PREFIX + token, JSON.toJSONString(payload), TTL);
            recordAuth("portal", null, "免认证终端", mac, ip, 1, "免认证终端放行");
            log.info("Portal 免认证放行: ip={} mac={}", ip, mac);
            return token;
        }
        try {
            smsService.verifyCode(phone, code);
        } catch (RuntimeException e) {
            recordAuth("portal", null, SensitiveUtil.maskPhone(phone), mac, ip, 0, "短信验证码错误");
            throw e;
        }
        String token = IdUtil.fastSimpleUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("mac", mac);
        payload.put("ip", ip);
        payload.put("phoneMask", SensitiveUtil.maskPhone(phone));
        payload.put("ts", System.currentTimeMillis());
        redisUtil.set(PORTAL_TOKEN_PREFIX + token, JSON.toJSONString(payload), TTL);
        recordAuth("portal", null, SensitiveUtil.maskPhone(phone), mac, ip, 1, "认证成功");
        log.info("Portal 认证成功: ip={} mac={} phone={}", ip, mac, SensitiveUtil.maskPhone(phone));
        return token;
    }

    /**
     * Portal 账号认证：终端用户输入账号+密码，校验 sys_user（BCrypt），通过后颁发 portalToken。
     * 复用登录失败锁定（防爆破），统一错误信息防账号枚举。
     */
    public String authAccount(String username, String password, String mac, String ip) {
        // 免认证终端直接放行
        if (exemptService.isExempt(mac, ip)) {
            String token = IdUtil.fastSimpleUUID();
            Map<String, Object> payload = new HashMap<>();
            payload.put("mac", mac);
            payload.put("ip", ip);
            payload.put("username", username == null ? "" : username);
            payload.put("authType", "account");
            payload.put("ts", System.currentTimeMillis());
            redisUtil.set(PORTAL_TOKEN_PREFIX + token, JSON.toJSONString(payload), TTL);
            recordAuth("portal", username, null, mac, ip, 1, "免认证终端放行");
            return token;
        }
        if (username == null || username.isBlank() || password == null) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (loginAttemptService.isIpLocked(ip)) throw new BusinessException(ResultCode.IP_LOCKED);
        if (loginAttemptService.isUserLocked(username)) throw new BusinessException(ResultCode.ACCOUNT_LOCKED);

        SysUser user = sysUserMapper.selectByUsername(username.trim());
        boolean ok = user != null && user.getStatus() != null && user.getStatus() == 1
                && user.getPasswordHash() != null && passwordEncoder.matches(password, user.getPasswordHash());
        if (!ok) {
            loginAttemptService.recordFail(ip, username.trim());
            recordAuth("portal", username, null, mac, ip, 0, "账号或密码错误");
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        loginAttemptService.clear(ip, username.trim());
        String token = IdUtil.fastSimpleUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("mac", mac);
        payload.put("ip", ip);
        payload.put("username", user.getUsername());
        payload.put("authType", "account");
        payload.put("ts", System.currentTimeMillis());
        redisUtil.set(PORTAL_TOKEN_PREFIX + token, JSON.toJSONString(payload), TTL);
        recordAuth("portal", user.getUsername(), null, mac, ip, 1, "账号认证成功");
        log.info("Portal 账号认证成功: user={} ip={} mac={}", user.getUsername(), ip, mac);
        return token;
    }

    private void recordAuth(String type, String username, String phone, String mac, String ip, int result, String msg) {
        try {
            AuthLog l = new AuthLog();
            l.setAuthType(type);
            l.setUsername(username);
            l.setPhone(phone);
            l.setMac(mac);
            l.setIp(ip);
            l.setResult(result);
            l.setMessage(msg);
            authLogMapper.insert(l);
            syslogForwarder.forwardAuthLog("authLog type=" + type + " user=" + nz(username)
                    + " phone=" + nz(phone) + " mac=" + nz(mac) + " ip=" + nz(ip)
                    + " result=" + result + " msg=" + nz(msg));
        } catch (Exception e) {
            log.warn("写认证日志失败: {}", e.getMessage());
        }
    }

    private static String nz(String s) { return s == null ? "-" : s; }
}
