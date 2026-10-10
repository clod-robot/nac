package com.nac.radius.netty;

import com.nac.radius.service.PortalV2Service;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Portal v2.0 UDP 服务端（默认端口 2000）。
 * 由页面开关 portalV2Enabled 控制；后台线程每 10s 对账一次配置，实现开关/端口热生效（无需重启）。
 */
@Slf4j
@Component
public class PortalV2Server {

    private final PortalV2Service service;
    private NioEventLoopGroup group;
    private Channel channel;
    private volatile int boundPort = -1;
    private final ScheduledExecutorService reconciler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "portal-v2-reconcile");
        t.setDaemon(true);
        return t;
    });

    public PortalV2Server(PortalV2Service service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        group = new NioEventLoopGroup(1);
        reconcile();
        reconciler.scheduleWithFixedDelay(this::reconcile, 10, 10, TimeUnit.SECONDS);
    }

    /** 对账开关与端口，热生效。 */
    public void reconcile() {
        try {
            boolean enabled = service.isEnabled();
            int port = service.getPort();
            if (!enabled) {
                if (channel != null) {
                    closeChannel();
                    log.info("Portal v2 已关闭，停止监听 udp/{}", boundPort);
                }
                return;
            }
            if (channel == null || !channel.isActive() || boundPort != port) {
                if (channel != null) closeChannel();
                bind(port);
            }
        } catch (Exception e) {
            log.error("Portal v2 对账异常: {}", e.getMessage());
        }
    }

    /** 运行时状态（供管理页面展示当前实际监听的协议/端口）。 */
    public java.util.Map<String, Object> status() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        boolean enabled = service.isEnabled();
        boolean running = channel != null && channel.isActive();
        m.put("enabled", enabled);
        m.put("running", running);
        m.put("port", running ? boundPort : service.getPort());
        String proto = service.protocolConfig();
        m.put("protocol", proto);
        // 实际对外提供的版本：运行中按配置，未运行则为“未启用”
        m.put("activeVersion", running ? protoVersionLabel(proto) : "未启用");
        return m;
    }

    private static String protoVersionLabel(String proto) {
        if (proto == null || proto.isBlank() || "both".equalsIgnoreCase(proto)) return "Portal 1.0 / 2.0（兼容）";
        if ("v1".equalsIgnoreCase(proto) || "1".equals(proto)) return "Portal 1.0";
        if ("v2".equalsIgnoreCase(proto) || "2".equals(proto)) return "Portal 2.0";
        return proto;
    }

    private void bind(int port) {
        try {
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) {
                            ch.pipeline().addLast(new PortalV2Handler(service));
                        }
                    });
            channel = b.bind(port).sync().channel();
            boundPort = port;
            log.info("Portal v2 服务已启动: udp/{}", port);
        } catch (Exception e) {
            log.error("Portal v2 绑定 udp/{} 失败: {}", port, e.getMessage());
            channel = null;
            boundPort = -1;
        }
    }

    private void closeChannel() {
        try { if (channel != null) channel.close().sync(); } catch (Exception ignored) {}
        channel = null;
        boundPort = -1;
    }

    @PreDestroy
    public void stop() {
        reconciler.shutdownNow();
        closeChannel();
        if (group != null) group.shutdownGracefully();
        log.info("Portal v2 服务已停止");
    }
}
