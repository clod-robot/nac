package com.nac.common.syslog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.redis.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日志外发：将操作日志/认证日志按 syslog(RFC3164) 报文发往配置的 syslog 服务器。
 * - 配置存 Redis，进程内缓存并每 5s 惰性刷新，保存后调用 refresh() 立即生效。
 * - 发送失败/未启用均静默降级，绝不影响主业务流程与日志落库。
 * - 全部在调用线程同步执行，但设置极短超时，避免拖慢业务。
 */
@Slf4j
@Component
public class SyslogForwarder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter SYSLOG_TS = DateTimeFormatter.ofPattern("MMM d HH:mm:ss");
    private static final long REFRESH_MS = 5_000L;
    private static final int MAX_MSG = 1024; // syslog UDP 单包建议 < 1024 字节

    private final RedisUtil redisUtil;

    private volatile SyslogConfig cached;
    private volatile long lastFetch = 0L;
    private volatile String localHost;

    public SyslogForwarder(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    /** 读取生效配置（带默认值兜底）。 */
    public SyslogConfig getConfig() {
        long now = System.currentTimeMillis();
        SyslogConfig c = cached;
        if (c == null || now - lastFetch > REFRESH_MS) {
            c = loadFromRedis();
            cached = c;
            lastFetch = now;
        }
        return c;
    }

    /** 保存配置后调用，强制下次读取重新拉取。 */
    public void refresh() {
        cached = null;
        lastFetch = 0L;
    }

    private SyslogConfig loadFromRedis() {
        try {
            String json = redisUtil.get(RedisKeyConstants.SYSLOG_CONFIG);
            if (json != null && !json.isBlank()) {
                SyslogConfig c = MAPPER.readValue(json, SyslogConfig.class);
                if (c != null) return c;
            }
        } catch (Exception e) {
            log.warn("读取 syslog 配置失败，使用默认配置: {}", e.getMessage());
        }
        return new SyslogConfig();
    }

    /** 外发操作日志。 */
    public void forwardOpLog(String message) {
        SyslogConfig c = getConfig();
        if (!c.isEnabled() || !c.isForwardOpLog()) return;
        send(c, "OP", message);
    }

    /** 外发认证日志。 */
    public void forwardAuthLog(String message) {
        SyslogConfig c = getConfig();
        if (!c.isEnabled() || !c.isForwardAuthLog()) return;
        send(c, "AUTH", message);
    }

    /** 测试连通性：用给定配置发送一条测试报文，返回是否成功。 */
    public boolean test(SyslogConfig cfg) {
        if (cfg == null || cfg.getHost() == null || cfg.getHost().isBlank()) return false;
        try {
            return doSend(cfg, "TEST", "syslog 连通性测试");
        } catch (Exception e) {
            log.warn("syslog 测试发送失败: {}", e.getMessage());
            return false;
        }
    }

    private void send(SyslogConfig cfg, String tag, String message) {
        if (cfg.getHost() == null || cfg.getHost().isBlank()) return;
        try {
            doSend(cfg, tag, message);
        } catch (Exception e) {
            log.warn("syslog 外发失败 host={}: {}", cfg.getHost(), e.getMessage());
        }
    }

    private boolean doSend(SyslogConfig cfg, String tag, String message) throws Exception {
        String line = buildPacket(cfg, tag, message);
        byte[] data = line.getBytes(StandardCharsets.UTF_8);
        boolean tcp = "TCP".equalsIgnoreCase(cfg.getProtocol());
        InetAddress addr = InetAddress.getByName(cfg.getHost().trim());
        if (tcp) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(addr, cfg.getPort()), 800);
                s.setSoTimeout(800);
                try (OutputStream out = s.getOutputStream()) {
                    out.write(data);
                    out.write('\n');
                    out.flush();
                }
            }
        } else {
            try (DatagramSocket ds = new DatagramSocket()) {
                ds.setSoTimeout(800);
                ds.send(new DatagramPacket(data, data.length, addr, cfg.getPort()));
            }
        }
        return true;
    }

    /** 构造 RFC3164 syslog 报文：<PRI>timestamp hostname appName[tag]: message */
    private String buildPacket(SyslogConfig cfg, String tag, String message) {
        int facility = Math.max(0, Math.min(23, cfg.getFacility()));
        int severity = 6; // informational
        int pri = facility * 8 + severity;
        String ts = LocalDateTime.now().format(SYSLOG_TS);
        String host = localHost();
        String app = (cfg.getAppName() == null || cfg.getAppName().isBlank()) ? "nac" : cfg.getAppName();
        String msg = message == null ? "" : message;
        // 剔除换行，控制长度
        msg = msg.replace('\n', ' ').replace('\r', ' ');
        String packet = "<" + pri + ">" + ts + " " + host + " " + app + "[" + tag + "]: " + msg;
        if (packet.getBytes(StandardCharsets.UTF_8).length > MAX_MSG) {
            // 按字节截断到 MAX_MSG
            byte[] all = packet.getBytes(StandardCharsets.UTF_8);
            packet = new String(all, 0, MAX_MSG, StandardCharsets.UTF_8);
        }
        return packet;
    }

    private String localHost() {
        String h = localHost;
        if (h != null) return h;
        try {
            h = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            h = "nac-server";
        }
        localHost = h;
        return h;
    }
}
