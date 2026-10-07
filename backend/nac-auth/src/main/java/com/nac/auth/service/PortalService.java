package com.nac.auth.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.nac.auth.entity.AuthLog;
import com.nac.auth.entity.PortalConfig;
import com.nac.auth.mapper.AuthLogMapper;
import com.nac.auth.mapper.PortalConfigMapper;
import com.nac.common.redis.RedisUtil;
import com.nac.common.security.SensitiveUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final SmsService smsService;
    private final RedisUtil redisUtil;

    public PortalService(PortalConfigMapper configMapper, AuthLogMapper authLogMapper, SmsService smsService, RedisUtil redisUtil) {
        this.configMapper = configMapper;
        this.authLogMapper = authLogMapper;
        this.smsService = smsService;
        this.redisUtil = redisUtil;
    }

    public Map<String, String> config() {
        Map<String, String> map = new HashMap<>();
        try {
            for (PortalConfig c : configMapper.selectAll()) {
                map.put(c.getConfigKey(), c.getConfigValue());
            }
        } catch (Exception e) {
            log.warn("Portal 配置读取失败: {}", e.getMessage());
        }
        return map;
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
        } catch (Exception e) {
            log.warn("写认证日志失败: {}", e.getMessage());
        }
    }
}
