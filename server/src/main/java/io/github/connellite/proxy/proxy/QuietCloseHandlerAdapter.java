package io.github.connellite.proxy.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
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
