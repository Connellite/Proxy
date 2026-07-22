package io.github.connellite.proxy.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

public final class RelayHandler extends ChannelInboundHandlerAdapter {

    private final Channel relayChannel;
    private final LongConsumer onBytes;
    private final BooleanSupplier stillAllowed;

    public RelayHandler(Channel relayChannel, LongConsumer onBytes) {
        this(relayChannel, onBytes, null);
    }

    public RelayHandler(Channel relayChannel, LongConsumer onBytes, BooleanSupplier stillAllowed) {
        this.relayChannel = relayChannel;
        this.onBytes = onBytes;
        this.stillAllowed = stillAllowed;
    }

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
            if (stillAllowed != null && !stillAllowed.getAsBoolean()) {
                ReferenceCountUtil.release(msg);
                closeOnFlush(relayChannel);
                closeOnFlush(ctx.channel());
                return;
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

    public static void closeOnFlush(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
