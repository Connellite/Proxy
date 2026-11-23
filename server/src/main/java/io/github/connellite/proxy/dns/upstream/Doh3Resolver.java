package io.github.connellite.proxy.dns.upstream;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DNS-over-HTTP/3 client: POST {@code application/dns-message} on QUIC.
 */
public final class Doh3Resolver extends AbstractUpstreamDnsResolver {

    private static final String DNS_MESSAGE = "application/dns-message";

    private final String host;
    private final int port;
    private final String path;
    private final String authority;

    public Doh3Resolver(String host, int port, String httpsUri) {
        this.host = host;
        this.port = port;
        URI uri = URI.create(httpsUri);
        String rawPath = uri.getRawPath();
        this.path = rawPath == null || rawPath.isEmpty() ? "/dns-query" : rawPath;
        this.authority = host.indexOf(':') >= 0
                ? "[" + host + "]:" + port
                : host + (port == 443 ? "" : ":" + port);
    }

    @Override
    public CompletionStage<Message> sendAsync(Message query, Executor executor) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        executor.execute(() -> send(query, future));
        return future;
    }

    private void send(Message query, CompletableFuture<Message> future) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        long timeoutMs = Math.max(1, getTimeout().toMillis());
        AtomicReference<ByteBuf> body = new AtomicReference<>(Unpooled.buffer());
        try {
            QuicUpstream.requireAvailable("DoH3");
            byte[] wire = query.toWire(65535);
            QuicSslContext ssl = QuicUpstream.clientContext(Http3.supportedApplicationProtocols());
            Channel datagram = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(Http3.newQuicClientCodecBuilder()
                            .sslContext(ssl)
                            .maxIdleTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .initialMaxData(10_000_000)
                            .initialMaxStreamDataBidirectionalLocal(1_000_000)
                            .initialMaxStreamsBidirectional(100)
                            .build())
                    .bind(0).sync().channel();
            QuicChannel quic = QuicChannel.newBootstrap(datagram)
                    .handler(new Http3ClientConnectionHandler())
                    .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) {
                        }
                    })
                    .remoteAddress(InetSocketAddress.createUnresolved(host, port))
                    .connect()
                    .get(timeoutMs, TimeUnit.MILLISECONDS);

            QuicStreamChannel stream = Http3.newRequestStream(quic, new Http3RequestStreamInboundHandler() {
                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
                    ReferenceCountUtil.release(frame);
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
                    try {
                        body.get().writeBytes(frame.content());
                    } finally {
                        frame.release();
                    }
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    ByteBuf buf = body.getAndSet(Unpooled.EMPTY_BUFFER);
                    try {
                        byte[] bytes = new byte[buf.readableBytes()];
                        buf.readBytes(bytes);
                        future.complete(new Message(bytes));
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    } finally {
                        buf.release();
                        ctx.close();
                    }
                }
            }).sync().getNow();

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

            stream.eventLoop().schedule(() -> {
                if (future.completeExceptionally(new TimeoutException("DoH3 timed out"))) {
                    stream.close();
                    quic.close();
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);

            future.whenComplete((r, e) -> {
                ByteBuf leftover = body.getAndSet(Unpooled.EMPTY_BUFFER);
                if (leftover != Unpooled.EMPTY_BUFFER) {
                    leftover.release();
                }
                quic.close();
                datagram.close();
                group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            });
        } catch (Exception ex) {
            ByteBuf leftover = body.getAndSet(Unpooled.EMPTY_BUFFER);
            if (leftover != Unpooled.EMPTY_BUFFER) {
                leftover.release();
            }
            future.completeExceptionally(ex);
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
    }
}
