package io.github.connellite.proxy.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import lombok.extern.slf4j.Slf4j;

import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;

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

    /**
     * Peer already dropped the TCP connection. The JDK reports reset and broken pipe as
     * {@link SocketException} ({@code sun.net.ConnectionResetException} is one). A closed
     * channel is a {@link ClosedChannelException}. Connection refused, bind, no route and
     * port unreachable are other {@link SocketException} subclasses and stay visible.
     * A read timeout is an {@link java.net.SocketTimeoutException}, which is an
     * {@link java.io.InterruptedIOException}, so it is not a socket reset.
     */
    public static boolean isBenignClose(Throwable cause) {
        Throwable current = cause;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current instanceof ClosedChannelException || isPeerReset(current)) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
            depth++;
        }
        return false;
    }

    private static boolean isPeerReset(Throwable cause) {
        return cause instanceof SocketException
                && !(cause instanceof ConnectException)
                && !(cause instanceof BindException)
                && !(cause instanceof NoRouteToHostException)
                && !(cause instanceof PortUnreachableException);
    }
}
