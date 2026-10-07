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

/**
 * RADIUS UDP 服务端：监听 1812(认证) 与 1813(计费)。
 * 应用就绪后启动，销毁时优雅关闭事件循环组。
 */
@Slf4j
@Component
public class RadiusServer {

    private final RadiusProperties props;
    private final RadiusAuthService authService;
    private final RadiusAcctService acctService;
    private final RadiusSecretService secretService;
    private NioEventLoopGroup group;
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
        group = new NioEventLoopGroup(threads);
        try {
            authChannel = bind(props.getAuthPort(), true);
            acctChannel = bind(props.getAcctPort(), false);
            log.info("RADIUS 服务已启动: auth=udp/{} acct=udp/{} workers={}",
                    props.getAuthPort(), props.getAcctPort(), threads);
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
                        ch.pipeline().addLast(new RadiusChannelHandler(auth, authService, acctService, props, secretService));
                    }
                });
        return b.bind(port).sync().channel();
    }

    @PreDestroy
    public void stop() {
        try { if (authChannel != null) authChannel.close().sync(); } catch (Exception ignored) {}
        try { if (acctChannel != null) acctChannel.close().sync(); } catch (Exception ignored) {}
        if (group != null) group.shutdownGracefully();
        log.info("RADIUS 服务已停止");
    }
}
