package com.nac.auth.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.nac.auth.entity.AuthLog;
import com.nac.auth.entity.OnlineSession;
import com.nac.auth.entity.PortalConfig;
import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.AuthLogMapper;
import com.nac.auth.mapper.OnlineSessionMapper;
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
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import java.util.ArrayList;
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
    private final OnlineSessionMapper onlineSessionMapper;
    private final SysUserMapper sysUserMapper;
    private final SmsService smsService;
    private final RedisUtil redisUtil;
    private final ExemptTerminalService exemptService;
    private final SyslogForwarder syslogForwarder;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public PortalService(PortalConfigMapper configMapper, AuthLogMapper authLogMapper, OnlineSessionMapper onlineSessionMapper,
                         SysUserMapper sysUserMapper,
                         SmsService smsService, RedisUtil redisUtil, ExemptTerminalService exemptService,
                         SyslogForwarder syslogForwarder, PasswordEncoder passwordEncoder,
                         LoginAttemptService loginAttemptService) {
        this.configMapper = configMapper;
        this.authLogMapper = authLogMapper;
        this.onlineSessionMapper = onlineSessionMapper;
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

    /** 批量保存：一次 SQL 写入所有键（原逐键 upsert 会带来 N 次库往返，故较慢）。 */
    @Transactional
    public void updateConfig(Map<String, String> kv) {
        if (kv == null || kv.isEmpty()) return;
        List<PortalConfig> list = new ArrayList<>(kv.size());
        for (Map.Entry<String, String> e : kv.entrySet()) {
            PortalConfig c = new PortalConfig();
            c.setConfigKey(e.getKey());
            c.setConfigValue(e.getValue());
            c.setRemark("admin");
            list.add(c);
        }
        configMapper.batchUpsert(list);
    }

    /** 短信验证码通过后颁发 portalToken，记录上网 IP/MAC/SSID 等（脱敏），并写入在线会话。 */
    public String auth(String phone, String code, String mac, String ip,
                       String nasIp, String ssid, String nasName) {
        // 免认证终端：MAC/IP 命中白名单直接放行，无需短信验证码
        if (exemptService.isExempt(mac, ip)) {
            String token = IdUtil.fastSimpleUUID();
            Map<String, Object> payload = new HashMap<>();
            payload.put("mac", mac);
            payload.put("ip", ip);
            payload.put("phoneMask", "免认证终端");
            payload.put("ts", System.currentTimeMillis());
            redisUtil.set(PORTAL_TOKEN_PREFIX + token, JSON.toJSONString(payload), TTL);
            recordAuth("portal", null, "免认证终端", mac, ip, nasIp, ssid, nasName, 1, "免认证终端放行");
            upsertOnline("免认证终端", mac, ip, nasIp, ssid, nasName);
            log.info("Portal 免认证放行: ip={} mac={}", ip, mac);
            return token;
        }
        try {
            smsService.verifyCode(phone, code);
        } catch (RuntimeException e) {
            recordAuth("portal", null, SensitiveUtil.maskPhone(phone), mac, ip, nasIp, ssid, nasName, 0, "短信验证码错误");
            throw e;
        }
        String token = IdUtil.fastSimpleUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("mac", mac);
        payload.put("ip", ip);
        payload.put("phoneMask", SensitiveUtil.maskPhone(phone));
        payload.put("ts", System.currentTimeMillis());
        redisUtil.set(PORTAL_TOKEN_PREFIX + token, JSON.toJSONString(payload), TTL);
        recordAuth("portal", null, SensitiveUtil.maskPhone(phone), mac, ip, nasIp, ssid, nasName, 1, "认证成功");
        upsertOnline(SensitiveUtil.maskPhone(phone), mac, ip, nasIp, ssid, nasName);
        log.info("Portal 认证成功: ip={} mac={} phone={} ssid={}", ip, mac, SensitiveUtil.maskPhone(phone), ssid);
        return token;
    }

    /**
     * Portal 账号认证：终端用户输入账号+密码，校验 sys_user（BCrypt），通过后颁发 portalToken。
     * 复用登录失败锁定（防爆破），统一错误信息防账号枚举。
     */
    public String authAccount(String username, String password, String mac, String ip,
                              String nasIp, String ssid, String nasName) {
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
            recordAuth("portal", username, null, mac, ip, nasIp, ssid, nasName, 1, "免认证终端放行");
            upsertOnline(username, mac, ip, nasIp, ssid, nasName);
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
            recordAuth("portal", username, null, mac, ip, nasIp, ssid, nasName, 0, "账号或密码错误");
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
        recordAuth("portal", user.getUsername(), null, mac, ip, nasIp, ssid, nasName, 1, "账号认证成功");
        upsertOnline(user.getUsername(), mac, ip, nasIp, ssid, nasName);
        log.info("Portal 账号认证成功: user={} ip={} mac={} ssid={}", user.getUsername(), ip, mac, ssid);
        return token;
    }

    private void recordAuth(String type, String username, String phone, String mac, String ip,
                            String nasIp, String ssid, String nasName, int result, String msg) {
        try {
            AuthLog l = new AuthLog();
            l.setAuthType(type);
            l.setUsername(username);
            l.setPhone(phone);
            l.setMac(mac);
            l.setIp(ip);
            l.setNasIp(nasIp);
            l.setSsid(ssid);
            l.setNasName(nasName);
            l.setResult(result);
            l.setMessage(msg);
            authLogMapper.insert(l);
            syslogForwarder.forwardAuthLog("authLog type=" + type + " user=" + nz(username)
                    + " phone=" + nz(phone) + " mac=" + nz(mac) + " ip=" + nz(ip) + " nas=" + nz(nasIp)
                    + " ssid=" + nz(ssid) + " result=" + result + " msg=" + nz(msg));
        } catch (Exception e) {
            log.warn("写认证日志失败: {}", e.getMessage());
        }
    }

    /**
     * Portal 网页认证成功后写入在线会话（sys_online_session），使在线终端可见。
     * 会话ID以 MAC 优先（无 MAC 时用 IP）保证同一终端重复认证时幂等覆盖。
     */
    private void upsertOnline(String user, String mac, String ip, String nasIp, String ssid, String nasName) {
        try {
            String key = (mac != null && !mac.isBlank()) ? mac : ip;
            if (key == null || key.isBlank()) return;
            OnlineSession s = new OnlineSession();
            s.setAcctSessionId("portal-web:" + key);
            String name = (user == null || user.isBlank()) ? "portal" : user;
            s.setUsername(name);
            s.setUsernameMask(SensitiveUtil.mask(name, 1, 1));
            s.setMac(mac);
            s.setNasIp(nasIp);
            s.setFramedIp(ip);
            s.setSsid(ssid);
            s.setNasName(nasName);
            s.setStatus(1);
            s.setStartTime(java.time.LocalDateTime.now());
            onlineSessionMapper.upsert(s);
        } catch (Exception e) {
            log.warn("Portal 在线会话写入失败: {}", e.getMessage());
        }
    }

    private static String nz(String s) { return s == null ? "-" : s; }
}
