package io.github.connellite.proxy.dns.upstream;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import org.xbill.DNS.Message;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DNS-over-TLS client: length-prefixed DNS on one TLS connection.
 */
public final class DotResolver extends AbstractUpstreamDnsResolver {

    private final String host;
    private final int port;

    public DotResolver(String host, int port) {
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
        byte[] wire;
        try {
            wire = query.toWire(65535);
        } catch (Exception ex) {
            future.completeExceptionally(ex);
            return;
        }
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        long timeoutMs = Math.max(1, getTimeout().toMillis());
        try {
            SslContext ssl = SslContextBuilder.forClient().build();
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            SslHandler sslHandler = ssl.newHandler(ch.alloc(), host, port);
                            ch.pipeline().addLast(sslHandler);
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                            ch.pipeline().addLast(new LengthFieldPrepender(2));
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
                    });
            bootstrap.connect(new InetSocketAddress(host, port)).addListener((ChannelFuture connect) -> {
                if (!connect.isSuccess()) {
                    future.completeExceptionally(connect.cause());
                    return;
                }
                Channel channel = connect.channel();
                ByteBuf buf = channel.alloc().buffer(wire.length);
                buf.writeBytes(wire);
                channel.writeAndFlush(buf).addListener((ChannelFuture write) -> {
                    if (!write.isSuccess()) {
                        future.completeExceptionally(write.cause());
                        channel.close();
                    }
                });
                channel.eventLoop().schedule(() -> {
                    if (future.completeExceptionally(new TimeoutException("DoT timed out"))) {
                        channel.close();
                    }
                }, timeoutMs, TimeUnit.MILLISECONDS);
            });
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        }
        future.whenComplete((r, e) -> group.shutdownGracefully(0, 1, TimeUnit.SECONDS));
    }
}
