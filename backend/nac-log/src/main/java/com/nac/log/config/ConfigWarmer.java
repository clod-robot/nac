package com.nac.log.config;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.entity.SysConfig;
import com.nac.common.redis.RedisUtil;
import com.nac.log.mapper.ConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时将 DB 中的 syslog 配置回填到 Redis（仅当 Redis 缺失时），
 * 保证 SyslogForwarder 在 Redis 重启后仍能拿到正确配置。
 */
@Slf4j
@Component
public class ConfigWarmer implements ApplicationRunner {

    private final ConfigMapper configMapper;
    private final RedisUtil redis;

    public ConfigWarmer(ConfigMapper configMapper, RedisUtil redis) {
        this.configMapper = configMapper;
        this.redis = redis;
    }

    @Override
    public void run(ApplicationArguments args) {
        warm(RedisKeyConstants.SYSLOG_CONFIG);
    }

    private void warm(String key) {
        try {
            if (redis.get(key) != null) return;
            SysConfig c = configMapper.selectByKey(key);
            if (c != null && c.getConfigValue() != null && !c.getConfigValue().isEmpty()) {
                redis.set(key, c.getConfigValue());
                log.info("配置回填 Redis: {}", key);
            }
        } catch (Exception e) {
            log.warn("配置回填失败 {}: {}", key, e.getMessage());
        }
    }
}
