package com.nac.radius.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RADIUS 服务配置。密钥通过环境变量注入，禁止硬编码生产密钥。
 */
@Data
@Component
@ConfigurationProperties(prefix = "nac.radius")
public class RadiusProperties {

    /** 认证端口，默认 1812 */
    private int authPort = 1812;
    /** 计费端口，默认 1813 */
    private int acctPort = 1813;
    /** 与 NAS 约定的共享密钥 */
    private String sharedSecret = "NacRadius@2026";
    /** Netty boss/worker 线程数（UDP 单事件循环组，0=CPU 核数*2） */
    private int workerThreads = 0;
    /** 认证/计费业务处理线程池大小（BCrypt/DB 等重活从 IO 线程卸出，避免单通道串行瓶颈），0=CPU 核数*2 */
    private int bizThreads = 0;
    /** 业务线程池等待队列容量，满则丢弃新请求（防 OOM/洪泛） */
    private int bizQueueCapacity = 2000;
    /** 单用户认证失败锁定阈值（防爆破），超过则该用户 N 秒内拒绝 */
    private int failLockThreshold = 5;
    /** 锁定时长秒 */
    private int failLockSeconds = 300;
    /** 授权会话时长（秒），下发 Session-Timeout */
    private int sessionTimeout = 28800;
    /** 授权 VLAN（Tunnel-Private-Group-Id），0=不下发 */
    private int vlanId = 0;
    /** 802.1X EAP 方法：MD5 / TLS / PEAP */
    private String eapMethod = "MD5";
}
