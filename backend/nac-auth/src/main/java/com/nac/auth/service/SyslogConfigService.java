package com.nac.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.auth.mapper.ConfigMapper;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.ConfigCache;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.ResultCode;
import com.nac.common.syslog.SyslogConfig;
import com.nac.common.syslog.SyslogForwarder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * syslog 外发配置存取（cache-aside）：DB 为真值，Redis 缓存，热生效。
 */
@Slf4j
@Service
public class SyslogConfigService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String K = RedisKeyConstants.SYSLOG_CONFIG;

    private final RedisUtil redisUtil;
    private final SyslogForwarder forwarder;
    private final ConfigMapper configMapper;

    public SyslogConfigService(RedisUtil redisUtil, SyslogForwarder forwarder, ConfigMapper configMapper) {
        this.redisUtil = redisUtil;
        this.forwarder = forwarder;
        this.configMapper = configMapper;
    }

    public SyslogConfig get() {
        return forwarder.getConfig();
    }

    /** 保存配置并校验：先落库再刷缓存，保存后即时生效。 */
    public void save(SyslogConfig cfg) {
        if (cfg == null) throw new BusinessException(ResultCode.BAD_REQUEST, "配置不能为空");
        String proto = cfg.getProtocol() == null ? "UDP" : cfg.getProtocol().trim().toUpperCase();
        if (!"UDP".equals(proto) && !"TCP".equals(proto)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "协议仅支持 UDP/TCP");
        }
        cfg.setProtocol(proto);
        if (cfg.getPort() < 1 || cfg.getPort() > 65535) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "端口范围 1-65535");
        }
        if (cfg.getFacility() < 0 || cfg.getFacility() > 23) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "facility 范围 0-23");
        }
        if (cfg.getAppName() == null || cfg.getAppName().isBlank()) cfg.setAppName("nac");
        if (cfg.isEnabled() && (cfg.getHost() == null || cfg.getHost().isBlank())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "启用外发时必须填写服务器地址");
        }
        try {
            String json = MAPPER.writeValueAsString(cfg);
            ConfigCache.put(redisUtil, K, json, () -> configMapper.upsert(K, json));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "保存配置失败");
        }
        forwarder.refresh();
    }

    /** 用当前保存的配置发送测试报文。 */
    public boolean test() {
        return forwarder.test(get());
    }
}
