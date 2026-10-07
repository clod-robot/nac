package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PortalConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String remark;
    private LocalDateTime updateTime;
}
