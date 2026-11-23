package io.github.connellite.proxy.dns.server;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.proxy.IdleCloseHandler;
import io.github.connellite.proxy.proxy.QuietCloseHandlerAdapter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/** DNS-over-TLS. Length-prefixed DNS messages on one TLS connection. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DotServer implements Listener {

    private final DnsServer dns;
    private final ProxyProperties properties;

    private EventLoopGroup group;
    private Channel server;

    @Override
    public synchronized boolean isRunning() {
        return server != null && server.isActive();
    }

    @Override
    public String getProtocol() {
        return "DoT";
    }

    public synchronized void start(String bindHost, int port, SslContext ssl) throws InterruptedException {
        stop();
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        server = new ServerBootstrap().group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(ssl.newHandler(ch.alloc()));
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS));
                        ch.pipeline().addLast(new IdleCloseHandler());
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                byte[] query = new byte[msg.readableBytes()];
                                msg.readBytes(query);
                                InetSocketAddress client = ctx.channel().remoteAddress() instanceof InetSocketAddress remote
                                        ? remote : null;
                                if (!dns.exchange(query, client, getProtocol(), wire -> {
                                    ByteBuf out = ctx.alloc().buffer(2 + wire.length);
                                    out.writeShort(wire.length);
                                    out.writeBytes(wire);
                                    ctx.writeAndFlush(out);
                                })) {
                                    ctx.close();
                                }
                            }
                        });
                        QuietCloseHandlerAdapter.installLast(ch.pipeline());
                    }
                })
                .bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("DoT listening on {}:{}", bindHost, port);
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.close();
            server = null;
        }
        if (group != null) {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            group = null;
        }
    }
}
