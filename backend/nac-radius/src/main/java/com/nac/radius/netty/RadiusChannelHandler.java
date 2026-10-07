package com.nac.radius.netty;

import com.nac.radius.config.RadiusProperties;
import com.nac.radius.packet.RadiusCodec;
import com.nac.radius.packet.RadiusCodes;
import com.nac.radius.packet.RadiusPacket;
import com.nac.radius.service.RadiusAcctService;
import com.nac.radius.service.RadiusAuthService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

/**
 * UDP 报文处理器：解析 -> 认证/计费分发 -> 编码响应 -> 回包。
 * 单例无状态（依赖不可变服务），可被多端口共享。
 */
@Slf4j
public class RadiusChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final boolean authPort; // true=1812认证 false=1813计费
    private final RadiusAuthService authService;
    private final RadiusAcctService acctService;
    private final RadiusProperties props;

    public RadiusChannelHandler(boolean authPort, RadiusAuthService authService,
                                RadiusAcctService acctService, RadiusProperties props) {
        this.authPort = authPort;
        this.authService = authService;
        this.acctService = acctService;
        this.props = props;
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

        byte[] out = RadiusCodec.encodeResponse(response, request.getRequestAuthenticator(), props.getSharedSecret());
        ctx.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(out), msg.sender()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("RADIUS channel 异常: {}", cause.getMessage());
        // UDP 无连接，不关闭 ctx
    }
}
