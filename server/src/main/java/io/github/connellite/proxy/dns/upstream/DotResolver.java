package io.github.connellite.proxy.dns.upstream;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.xbill.DNS.Message;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * DNS-over-TLS client: length-prefixed DNS on one TLS connection.
 *
 * <p>https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/dot.go
 */
public final class DotResolver extends AbstractUpstreamDnsResolver {

    private final String host;
    private final int port;

    public DotResolver(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    protected String protocol() {
        return "DoT";
    }

    @Override
    protected void send(Message query, CompletableFuture<Message> future) {
        byte[] wire = query.toWire(65535);
        int expectedId = query.getHeader().getID();
        SslContext ssl = TlsHolder.CONTEXT;
        Bootstrap bootstrap = new Bootstrap()
                .group(group())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Math.min(Integer.MAX_VALUE, timeoutMs()))
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(ssl.newHandler(ch.alloc(), host, port));
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                        ch.pipeline().addLast(new LengthFieldPrepender(2));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                byte[] bytes = new byte[msg.readableBytes()];
                                msg.readBytes(bytes);
                                try {
                                    future.complete(response(query, bytes, expectedId));
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
                        });
                    }
                });
        ChannelFuture connect = bootstrap.connect(new InetSocketAddress(host, port));
        future.whenComplete((r, e) -> connect.channel().close());
        connect.addListener((ChannelFuture done) -> {
            if (!done.isSuccess()) {
                future.completeExceptionally(done.cause());
                return;
            }
            Channel channel = done.channel();
            ByteBuf buf = channel.alloc().buffer(wire.length);
            buf.writeBytes(wire);
            channel.writeAndFlush(buf).addListener((ChannelFuture write) -> {
                if (!write.isSuccess()) {
                    future.completeExceptionally(write.cause());
                }
            });
        });
    }

    private static final class TlsHolder {
        private static final SslContext CONTEXT = build();

        private static SslContext build() {
            try {
                return SslContextBuilder.forClient()
                        .endpointIdentificationAlgorithm("HTTPS")
                        .build();
            } catch (SSLException ex) {
                throw new IllegalStateException("Cannot create DoT client TLS context", ex);
            }
        }
    }
}
