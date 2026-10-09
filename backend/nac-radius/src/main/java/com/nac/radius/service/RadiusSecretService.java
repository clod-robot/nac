package com.nac.radius.service;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.entity.SysConfig;
import com.nac.common.redis.ConfigCache;
import com.nac.common.redis.RedisUtil;
import com.nac.radius.config.RadiusProperties;
import com.nac.radius.mapper.ConfigMapper;
import org.springframework.stereotype.Service;

/**
 * RADIUS 共享密钥解析（cache-aside）：Redis 缓存 → 未命中查 sys_config 回填 → 仍空回退默认配置。
 * 写入方（NAS 管理页）先落库再刷缓存，故 Redis 与 DB 始终一致。
 */
@Service
public class RadiusSecretService {

    private final RedisUtil redisUtil;
    private final RadiusProperties props;
    private final ConfigMapper configMapper;

    public RadiusSecretService(RedisUtil redisUtil, RadiusProperties props, ConfigMapper configMapper) {
        this.redisUtil = redisUtil;
        this.props = props;
        this.configMapper = configMapper;
    }

    public String getSharedSecret() {
        String k = RedisKeyConstants.RADIUS_SHARED_SECRET;
        return ConfigCache.get(redisUtil, k,
                () -> { SysConfig c = configMapper.selectByKey(k); return c == null ? null : c.getConfigValue(); },
                props.getSharedSecret());
    }
}
