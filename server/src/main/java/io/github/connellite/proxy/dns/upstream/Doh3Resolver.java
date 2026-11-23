package io.github.connellite.proxy.dns.upstream;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3Headers;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import org.xbill.DNS.Message;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * DNS-over-HTTP/3 client: POST {@code application/dns-message} on QUIC.
 *
 * <p>https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/doh.go
 */
public final class Doh3Resolver extends AbstractUpstreamDnsResolver {

    private static final String DNS_MESSAGE = "application/dns-message";

    private static final int MAX_MESSAGE_SIZE = 65535;

    private final String host;
    private final int port;
    private final String path;
    private final String authority;

    public Doh3Resolver(String host, int port, String httpsUri) {
        this.host = host;
        this.port = port;
        URI uri = URI.create(httpsUri);
        String rawPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        this.path = uri.getRawQuery() == null ? rawPath : rawPath + "?" + uri.getRawQuery();
        String bracketed = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        this.authority = port == 443 ? bracketed : bracketed + ":" + port;
    }

    @Override
    protected String protocol() {
        return "DoH3";
    }

    @Override
    protected void send(Message query, CompletableFuture<Message> future) throws Exception {
        QuicUpstream.requireAvailable("DoH3");
        long timeoutMs = timeoutMs();
        // RFC 8484 §4.1: clients SHOULD use DNS ID 0 for cache friendliness.
        byte[] wire = withZeroId(query).toWire(MAX_MESSAGE_SIZE);
        QuicSslContext ssl = QuicUpstream.doh3Context();
        Channel datagram = new Bootstrap()
                .group(group())
                .channel(NioDatagramChannel.class)
                .handler(Http3.newQuicClientCodecBuilder()
                        .sslContext(ssl)
                        .sslEngineProvider(QuicUpstream.engineFor(ssl, host, port))
                        .maxIdleTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .initialMaxData(10_000_000)
                        .initialMaxStreamDataBidirectionalLocal(1_000_000)
                        .initialMaxStreamsBidirectional(100)
                        .build())
                .bind(0).sync().channel();
        future.whenComplete((r, e) -> datagram.close());
        if (future.isDone()) {
            return;
        }
        QuicChannel quic = QuicChannel.newBootstrap(datagram)
                .handler(new Http3ClientConnectionHandler())
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                    }
                })
                .remoteAddress(QuicUpstream.resolve(host, port))
                .connect()
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        future.whenComplete((r, e) -> quic.close());

        QuicStreamChannel stream = Http3.newRequestStream(quic, new ResponseHandler(query, future))
                .get(timeoutMs, TimeUnit.MILLISECONDS);

        Http3Headers headers = new DefaultHttp3Headers();
        headers.method("POST").scheme("https").authority(authority).path(path);
        headers.set("content-type", DNS_MESSAGE);
        headers.set("accept", DNS_MESSAGE);
        headers.setInt("content-length", wire.length);
        stream.write(new DefaultHttp3HeadersFrame(headers));
        ByteBuf content = stream.alloc().buffer(wire.length);
        content.writeBytes(wire);
        stream.writeAndFlush(new DefaultHttp3DataFrame(content))
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }

    private static final class ResponseHandler extends Http3RequestStreamInboundHandler {

        private final Message query;
        private final CompletableFuture<Message> future;
        private final ByteBuf body = Unpooled.buffer();

        ResponseHandler(Message query, CompletableFuture<Message> future) {
            this.query = query;
            this.future = future;
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
            try {
                CharSequence status = frame.headers().status();
                if (status != null && !"200".contentEquals(status)) {
                    future.completeExceptionally(new IOException("DoH3 expected status 200, got " + status));
                    ctx.close();
                }
            } finally {
                ReferenceCountUtil.release(frame);
            }
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
            try {
                if (body.readableBytes() + frame.content().readableBytes() > MAX_MESSAGE_SIZE) {
                    future.completeExceptionally(new IOException("DoH3 response exceeds " + MAX_MESSAGE_SIZE));
                    ctx.close();
                    return;
                }
                body.writeBytes(frame.content());
            } finally {
                frame.release();
            }
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) {
            try {
                byte[] bytes = new byte[body.readableBytes()];
                body.readBytes(bytes);
                future.complete(response(query, bytes, 0));
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            } finally {
                ctx.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            future.completeExceptionally(cause);
            ctx.close();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            body.release();
        }
    }
}
