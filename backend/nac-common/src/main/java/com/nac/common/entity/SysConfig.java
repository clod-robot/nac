package com.nac.common.entity;

import lombok.Data;
import java.util.Date;

/**
 * 系统配置键值表（sys_config）：配置项的唯一持久化真值。
 * Redis 仅作为缓存，读写遵循 cache-aside：写先落库再刷缓存，读缓存未命中则查库回填。
 */
@Data
public class SysConfig {
    private String configKey;
    private String configValue;
    private Date updateTime;
}
