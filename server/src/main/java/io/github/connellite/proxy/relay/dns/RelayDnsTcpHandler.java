package io.github.connellite.proxy.relay.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;

import java.net.InetSocketAddress;

/**
 * Plain DNS over TCP (length-prefixed messages).
 */
@RequiredArgsConstructor
final class RelayDnsTcpHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final RelayDnsServer dns;

    String getProtocol() {
        return "TCP";
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte[] query = new byte[msg.readableBytes()];
        msg.readBytes(query);
        InetSocketAddress client = ctx.channel().remoteAddress() instanceof InetSocketAddress remote
                ? remote : null;
        if (!dns.exchange(query, client, getProtocol(), wire -> {
            ByteBuf out = ctx.alloc().buffer(2 + wire.length);
            out.writeShort(wire.length);
            out.writeBytes(wire);
            ctx.writeAndFlush(out).addListener(future -> ctx.close());
        })) {
            ctx.close();
        }
    }
}
