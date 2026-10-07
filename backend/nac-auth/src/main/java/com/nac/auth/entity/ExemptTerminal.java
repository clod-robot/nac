package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 免认证终端：绑定 MAC 或 IP，命中即放行认证（MAB 白名单）。 */
@Data
public class ExemptTerminal {
    private Long id;
    private String mac;
    private String ip;
    private String remark;
    private Integer enabled; // 1 启用 0 禁用
    private LocalDateTime createTime;
}
