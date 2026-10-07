package com.nac.common.syslog;

import lombok.Data;

/**
 * 日志外发 syslog 服务器配置（Redis 持久化，热生效）。
 * 操作日志与认证日志可按开关分别外发到同一 syslog 服务器。
 */
@Data
public class SyslogConfig {
    /** 总开关：是否启用外发 */
    private boolean enabled = false;
    /** syslog 服务器地址（IP/域名） */
    private String host = "";
    /** 端口，默认 514 */
    private int port = 514;
    /** 协议：UDP / TCP */
    private String protocol = "UDP";
    /** syslog facility（0-23），默认 16=local0 */
    private int facility = 16;
    /** 应用标识（syslog TAG 前缀） */
    private String appName = "nac";
    /** 是否外发操作日志 */
    private boolean forwardOpLog = true;
    /** 是否外发认证日志 */
    private boolean forwardAuthLog = true;
}
