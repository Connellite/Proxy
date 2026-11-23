package io.github.connellite.proxy.dns.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.RequiredArgsConstructor;

/**
 * Plain DNS over UDP.
 */
@RequiredArgsConstructor
final class RelayDnsUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final RelayDnsServer dns;

    String getProtocol() {
        return "UDP";
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        byte[] query = new byte[packet.content().readableBytes()];
        packet.content().readBytes(query);
        dns.exchange(query, packet.sender(), getProtocol(), wire -> {
            ByteBuf buf = Unpooled.wrappedBuffer(wire);
            ctx.writeAndFlush(new DatagramPacket(buf, packet.sender()));
        });
    }
}
