package com.nac.radius.netty;

import com.nac.radius.packet.PortalPacket;
import com.nac.radius.service.PortalV2Service;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/** Portal v2.0 UDP 报文处理器。 */
@Slf4j
public class PortalV2Handler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final PortalV2Service service;

    public PortalV2Handler(PortalV2Service service) {
        this.service = service;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        if (!service.isEnabled()) return; // 开关关闭则静默丢弃
        ByteBuf content = msg.content();
        byte[] data = new byte[content.readableBytes()];
        content.readBytes(data);
        PortalPacket req = PortalPacket.parse(data);
        if (req == null) {
            log.warn("丢弃非法 Portal 报文: from={}", msg.sender());
            return;
        }
        if (!service.versionAllowed(req.getVersion())) {
            log.warn("丢弃 Portal v{} 报文(协议版本未启用): from={}", req.getVersion(), msg.sender());
            return;
        }
        InetSocketAddress sender = msg.sender();
        PortalPacket resp = service.handle(req, sender);
        if (resp != null) {
            byte[] out = resp.encode();
            ctx.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(out), sender));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Portal v2 channel 异常: {}", cause.getMessage());
    }
}
