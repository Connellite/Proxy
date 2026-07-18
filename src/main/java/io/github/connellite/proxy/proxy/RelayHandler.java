package io.github.connellite.proxy.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;

import java.util.function.LongConsumer;

@RequiredArgsConstructor
class RelayHandler extends ChannelInboundHandlerAdapter {

    private final Channel relayChannel;
    private final LongConsumer onBytes;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            if (msg instanceof ByteBuf buf && onBytes != null) {
                int readable = buf.readableBytes();
                if (readable > 0) {
                    onBytes.accept(readable);
                }
            }
            relayChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            });
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeOnFlush(relayChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        closeOnFlush(ctx.channel());
    }

    static void closeOnFlush(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
