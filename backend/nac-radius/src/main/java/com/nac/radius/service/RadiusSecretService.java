package com.nac.radius.service;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.redis.RedisUtil;
import com.nac.radius.config.RadiusProperties;
import org.springframework.stereotype.Service;

/**
 * RADIUS 共享密钥解析：优先取 Redis（可在“NAS管理”页热修改），未配置时回退 nac.radius.shared-secret。
 */
@Service
public class RadiusSecretService {

    private final RedisUtil redisUtil;
    private final RadiusProperties props;

    public RadiusSecretService(RedisUtil redisUtil, RadiusProperties props) {
        this.redisUtil = redisUtil;
        this.props = props;
    }

    public String getSharedSecret() {
        String v = redisUtil.get(RedisKeyConstants.RADIUS_SHARED_SECRET);
        return (v == null || v.isEmpty()) ? props.getSharedSecret() : v;
    }
}
