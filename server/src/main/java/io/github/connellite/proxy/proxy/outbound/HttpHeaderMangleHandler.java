package io.github.connellite.proxy.proxy.outbound;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * Applies {@link HttpHeaderMangler} to every plain HTTP request head written to an upstream socket.
 * Must sit closer to the socket than the HTTP encoder: Netty lower-cases header names it encodes.
 * Behind {@code SslHandler} only TLS records arrive here, so nothing changes.
 */
final class HttpHeaderMangleHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof ByteBuf buf && buf.isReadable()) {
            ByteBuf mangled = HttpHeaderMangler.mangle(buf, ctx.alloc());
            if (mangled != null) {
                buf.release();
                ctx.write(mangled, promise);
                return;
            }
        }
        ctx.write(msg, promise);
    }
}
