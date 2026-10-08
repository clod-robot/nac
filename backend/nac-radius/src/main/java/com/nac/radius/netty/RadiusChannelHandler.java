package com.nac.radius.netty;

import com.nac.radius.config.RadiusProperties;
import com.nac.radius.packet.RadiusCodec;
import com.nac.radius.packet.RadiusCodes;
import com.nac.radius.packet.RadiusPacket;
import com.nac.radius.service.RadiusAcctService;
import com.nac.radius.service.RadiusAuthService;
import com.nac.radius.service.RadiusSecretService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

/**
 * UDP 报文处理器：解析 -> 认证/计费分发 -> 编码响应 -> 回包。
 * 单例无状态（依赖不可变服务），可被多端口共享。
 * 认证/计费等重活（BCrypt/DB/Kafka）提交到独立业务线程池，避免 UDP 单通道被 IO 线程串行化。
 */
@Slf4j
public class RadiusChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final boolean authPort; // true=1812认证 false=1813计费
    private final RadiusAuthService authService;
    private final RadiusAcctService acctService;
    private final RadiusProperties props;
    private final RadiusSecretService secretService;
    private final ExecutorService bizExecutor;

    public RadiusChannelHandler(boolean authPort, RadiusAuthService authService,
                                RadiusAcctService acctService, RadiusProperties props,
                                RadiusSecretService secretService, ExecutorService bizExecutor) {
        this.authPort = authPort;
        this.authService = authService;
        this.acctService = acctService;
        this.props = props;
        this.secretService = secretService;
        this.bizExecutor = bizExecutor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        ByteBuf content = msg.content();
        byte[] data = new byte[content.readableBytes()];
        content.readBytes(data);

        RadiusPacket request = RadiusCodec.parse(data, data.length);
        if (request == null) {
            log.warn("丢弃非法 RADIUS 报文: from={}", msg.sender());
            return;
        }
        request.setSender(msg.sender());

        // 入站 EAP 报文必须携带合法 Message-Authenticator（RFC 3579），否则丢弃，防篡改/伪造
        if (request.getAttr(RadiusCodes.EAP_MESSAGE) != null
                && !RadiusCodec.verifyMessageAuthenticator(data, secretService.getSharedSecret())) {
            log.warn("丢弃 EAP 报文：Message-Authenticator 校验失败 from={}", msg.sender());
            return;
        }

        // 重活异步化：业务线程池处理，IO 线程立即回收以承接后续报文
        try {
            bizExecutor.execute(() -> handleAndReply(ctx, msg, request));
        } catch (java.util.concurrent.RejectedExecutionException re) {
            // 队列已满（洪泛/过载）：丢弃，防止 OOM；客户端按 RADIUS 机制会重试
            log.warn("RADIUS 业务队列已满，丢弃请求: from={}", msg.sender());
        }
    }

    private void handleAndReply(ChannelHandlerContext ctx, DatagramPacket msg, RadiusPacket request) {
        RadiusPacket response = null;
        try {
            if (authPort && request.getCode() == RadiusCodes.ACCESS_REQUEST) {
                response = authService.authenticate(request);
            } else if (!authPort && request.getCode() == RadiusCodes.ACCOUNTING_REQUEST) {
                response = acctService.account(request);
            }
        } catch (Exception e) {
            log.error("RADIUS 处理异常: {}", e.getMessage(), e);
        }

        if (response == null) return; // 非期望类型，静默丢弃

        byte[] out = RadiusCodec.encodeResponse(response, request.getRequestAuthenticator(), secretService.getSharedSecret());
        // 从非 IO 线程写回是安全的：Netty 会把写操作投递到对应 event loop
        ctx.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(out), msg.sender()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("RADIUS channel 异常: {}", cause.getMessage());
        // UDP 无连接，不关闭 ctx
    }
}
