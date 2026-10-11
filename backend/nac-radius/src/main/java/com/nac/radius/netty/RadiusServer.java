package com.nac.radius.netty;

import com.nac.radius.config.RadiusProperties;
import com.nac.radius.service.RadiusAcctService;
import com.nac.radius.service.RadiusAuthService;
import com.nac.radius.service.RadiusSecretService;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RADIUS UDP 服务端：监听 1812(认证) 与 1813(计费)。
 * 应用就绪后启动，销毁时优雅关闭事件循环组与业务线程池。
 */
@Slf4j
@Component
public class RadiusServer {

    /** 出厂默认共享密钥（与 RadiusProperties/application.yml 默认一致），用于启动期弱配置检测。 */
    private static final String DEFAULT_SECRET = "NacRadius@2026";

    private final RadiusProperties props;
    private final RadiusAuthService authService;
    private final RadiusAcctService acctService;
    private final RadiusSecretService secretService;
    private NioEventLoopGroup group;
    private ExecutorService bizExecutor;
    private Channel authChannel;
    private Channel acctChannel;

    public RadiusServer(RadiusProperties props, RadiusAuthService authService, RadiusAcctService acctService,
                        RadiusSecretService secretService) {
        this.props = props;
        this.authService = authService;
        this.acctService = acctService;
        this.secretService = secretService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        int threads = props.getWorkerThreads() > 0 ? props.getWorkerThreads() : Runtime.getRuntime().availableProcessors() * 2;
        int bizThreads = props.getBizThreads() > 0 ? props.getBizThreads() : Runtime.getRuntime().availableProcessors() * 2;
        int queueCap = props.getBizQueueCapacity() > 0 ? props.getBizQueueCapacity() : 2000;
        AtomicInteger seq = new AtomicInteger();
        bizExecutor = new ThreadPoolExecutor(bizThreads, bizThreads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCap),
                r -> {
                    Thread t = new Thread(r, "radius-biz-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()); // 队列满则抛 RejectedExecutionException，由 handler 丢弃防 OOM
        group = new NioEventLoopGroup(threads);
        try {
            authChannel = bind(props.getAuthPort(), true);
            acctChannel = bind(props.getAcctPort(), false);
            log.info("RADIUS 服务已启动: auth=udp/{} acct=udp/{} workers={} bizThreads={} bizQueue={}",
                    props.getAuthPort(), props.getAcctPort(), threads, bizThreads, queueCap);
            // 安全加固：若生效密钥仍为出厂默认，强烈告警（RADIUS 共享密钥是防伪造的根基）
            String effective = secretService.getSharedSecret();
            if (DEFAULT_SECRET.equals(effective)) {
                log.error("【安全告警】RADIUS 共享密钥仍为出厂默认值(NacRadius@2026)，存在被伪造风险，请立即通过配置/环境变量 RADIUS_SHARED_SECRET 修改！");
            }
        } catch (Exception e) {
            log.error("RADIUS 服务启动失败，关闭事件循环组", e);
            stop();
        }
    }

    private Channel bind(int port, boolean auth) throws InterruptedException {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new RadiusChannelHandler(auth, authService, acctService, props, secretService, bizExecutor));
                    }
                });
        return b.bind(port).sync().channel();
    }

    @PreDestroy
    public void stop() {
        try { if (authChannel != null) authChannel.close().sync(); } catch (Exception ignored) {}
        try { if (acctChannel != null) acctChannel.close().sync(); } catch (Exception ignored) {}
        if (group != null) group.shutdownGracefully();
        if (bizExecutor != null) {
            bizExecutor.shutdown();
            try { bizExecutor.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        log.info("RADIUS 服务已停止");
    }
}
