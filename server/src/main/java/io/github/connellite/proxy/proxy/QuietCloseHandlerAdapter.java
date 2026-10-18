package io.github.connellite.proxy.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.Locale;

/**
 * Swallows connect-time resets on outbound sockets that have no other inbound handler yet.
 * Does not forward {@code exceptionCaught}, so Netty does not warn at the pipeline tail.
 */
@Slf4j
public final class QuietCloseHandlerAdapter extends ChannelInboundHandlerAdapter {

    /**
     * Keep this handler at the tail so a peer reset cannot fall through to Netty's default warning.
     * Handlers added later are moved back in front of it.
     */
    public static void installLast(ChannelPipeline pipeline) {
        QuietCloseHandlerAdapter existing = pipeline.get(QuietCloseHandlerAdapter.class);
        if (existing != null) {
            pipeline.remove(existing);
        }
        pipeline.addLast(new QuietCloseHandlerAdapter());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        handle(ctx, cause);
    }

    public static void handle(ChannelHandlerContext ctx, Throwable cause) {
        try {
            if (isBenignClose(cause)) {
                log.debug("Expected connection close: remote={}, cause={}", ctx.channel().remoteAddress(), cause.toString());
            } else {
                log.warn("Unexpected channel exception: remote={}", ctx.channel().remoteAddress(), cause);
            }
        } catch (Exception ignore) {
            // noop
        } finally {
            ctx.close();
        }
    }

    private static boolean isBenignClose(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof ClosedChannelException) {
                return true;
            }
            if (current instanceof SocketException) {
                String message = current.getMessage();
                if (message != null) {
                    String text = message.toLowerCase(Locale.ROOT);
                    if (text.contains("connection reset") || text.contains("broken pipe")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
