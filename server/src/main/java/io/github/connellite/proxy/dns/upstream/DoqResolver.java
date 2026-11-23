package io.github.connellite.proxy.dns.upstream;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import org.xbill.DNS.Message;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DNS-over-QUIC client: one length-prefixed DNS message per bidirectional stream.
 */
public final class DoqResolver extends AbstractUpstreamDnsResolver {

    private static final String ALPN = "doq";

    private final String host;
    private final int port;

    public DoqResolver(String host, int port) {
        this.host = host;
        this.port = port;
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
        try {
            QuicUpstream.requireAvailable("DoQ");
            byte[] wire = query.toWire(65535);
            QuicSslContext ssl = QuicUpstream.clientContext(ALPN);
            Channel datagram = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new QuicClientCodecBuilder()
                            .sslContext(ssl)
                            .maxIdleTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .initialMaxData(1_000_000)
                            .initialMaxStreamDataBidirectionalLocal(1_000_000)
                            .initialMaxStreamDataBidirectionalRemote(1_000_000)
                            .initialMaxStreamsBidirectional(1)
                            .build())
                    .bind(0).sync().channel();
            QuicChannel quic = QuicChannel.newBootstrap(datagram)
                    .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) {
                            ch.close();
                        }
                    })
                    .remoteAddress(InetSocketAddress.createUnresolved(host, port))
                    .connect()
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            QuicStreamChannel stream = quic.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) {
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                    byte[] bytes = new byte[msg.readableBytes()];
                                    msg.readBytes(bytes);
                                    try {
                                        future.complete(new Message(bytes));
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
                    }).sync().getNow();
            ByteBuf out = stream.alloc().buffer(2 + wire.length);
            out.writeShort(wire.length);
            out.writeBytes(wire);
            stream.writeAndFlush(out).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
            stream.eventLoop().schedule(() -> {
                if (future.completeExceptionally(new TimeoutException("DoQ timed out"))) {
                    stream.close();
                    quic.close();
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);
            future.whenComplete((r, e) -> {
                quic.close();
                datagram.close();
                group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            });
        } catch (Exception ex) {
            future.completeExceptionally(ex);
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
    }
}
