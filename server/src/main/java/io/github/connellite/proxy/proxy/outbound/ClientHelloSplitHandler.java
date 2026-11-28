package io.github.connellite.proxy.proxy.outbound;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Cuts the first TLS handshake record written to an upstream socket into two records.
 * <p>
 * Must sit closer to the socket than {@code SslHandler} and the upstream handshake codecs.
 * Writes that do not start a handshake record pass through. Before {@link #arm(ChannelPipeline)}
 * they do not use up the single cut, so a plain {@code CONNECT} or a SOCKS5 command is skipped.
 * Once armed, the next write either is the ClientHello or ends the attempt.
 */
final class ClientHelloSplitHandler extends ChannelOutboundHandlerAdapter {

    private final ClientHelloSplitMode mode;
    private final int position;
    private boolean armed;
    private boolean done;
    private ByteBuf pending;

    ClientHelloSplitHandler(ClientHelloSplitMode mode, int position, boolean armed) {
        this.mode = mode;
        this.position = position;
        this.armed = armed;
    }

    /**
     * The tunnel through the upstream is open: the next write decides whether there is a ClientHello to cut.
     */
    static void arm(ChannelPipeline pipeline) {
        ClientHelloSplitHandler handler = pipeline.get(ClientHelloSplitHandler.class);
        if (handler != null) {
            handler.armed = true;
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (done) {
            ctx.write(msg, promise);
            return;
        }
        if (!(msg instanceof ByteBuf buf)) {
            if (pending != null) {
                flushPending(ctx, ctx.newPromise(), null);
            }
            ctx.write(msg, promise);
            return;
        }
        if (pending == null) {
            if (!buf.isReadable()) {
                ctx.write(msg, promise);
                return;
            }
            if (!TlsRecordSplitter.startsWithClientHello(buf)) {
                if (armed) {
                    done = true;
                }
                ctx.write(msg, promise);
                return;
            }
            pending = ctx.alloc().buffer(buf.readableBytes());
        }
        pending.writeBytes(buf);
        buf.release();

        int recordLength = TlsRecordSplitter.recordLength(pending);
        if (!TlsRecordSplitter.startsWithClientHello(pending)
                || recordLength - TlsRecordSplitter.RECORD_HEADER > TlsRecordSplitter.MAX_FRAGMENT) {
            flushPending(ctx, promise, null);
            return;
        }
        if (recordLength < 0 || pending.readableBytes() < recordLength) {
            // The bytes are kept here; completing the write lets a relay that waits for it keep reading.
            promise.trySuccess();
            return;
        }
        ByteBuf fragment = pending.slice(pending.readerIndex() + TlsRecordSplitter.RECORD_HEADER,
                recordLength - TlsRecordSplitter.RECORD_HEADER);
        int offset = TlsRecordSplitter.splitOffset(fragment, mode, position, ThreadLocalRandom.current());
        ByteBuf head = null;
        if (offset > 0) {
            head = TlsRecordSplitter.split(pending.slice(pending.readerIndex(), recordLength), offset, ctx.alloc());
            pending.skipBytes(recordLength);
        }
        flushPending(ctx, promise, head);
    }

    private void flushPending(ChannelHandlerContext ctx, ChannelPromise promise, ByteBuf head) {
        done = true;
        ByteBuf rest = pending;
        pending = null;
        if (head == null) {
            ctx.write(rest, promise);
            return;
        }
        if (rest.isReadable()) {
            ctx.write(head);
            ctx.write(rest, promise);
        } else {
            rest.release();
            ctx.write(head, promise);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (pending != null) {
            pending.release();
            pending = null;
        }
    }
}
