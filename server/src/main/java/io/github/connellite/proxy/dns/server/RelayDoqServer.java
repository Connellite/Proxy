package io.github.connellite.proxy.dns.server;

import io.github.connellite.proxy.config.ProxyProperties;
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
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/** DNS-over-QUIC. One length-prefixed DNS message per client bidirectional stream. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayDoqServer implements RelayListener {

    private final RelayDnsServer dns;
    private final ProxyProperties properties;

    private EventLoopGroup group;
    private Channel server;

    @Override
    public synchronized boolean isRunning() {
        return server != null && server.isActive();
    }

    @Override
    public String getProtocol() {
        return "DoQ";
    }

    public synchronized void start(String bindHost, int port, QuicSslContext ssl) throws InterruptedException {
        if (!Quic.isAvailable()) {
            Throwable cause = Quic.unavailabilityCause();
            throw new IllegalStateException("DoQ native library is not available", cause);
        }
        stop();
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        long idleMillis = Math.max(1, properties.getIdleTimeoutSeconds()) * 1000L;
        server = new Bootstrap().group(group)
                .channel(NioDatagramChannel.class)
                .handler(new QuicServerCodecBuilder()
                        .sslContext(ssl)
                        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                        .maxIdleTimeout(idleMillis, TimeUnit.MILLISECONDS)
                        .initialMaxData(10_000_000)
                        .initialMaxStreamDataBidirectionalLocal(1_000_000)
                        .initialMaxStreamDataBidirectionalRemote(1_000_000)
                        .initialMaxStreamsBidirectional(100)
                        .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(QuicStreamChannel ch) {
                                if (ch.type() != QuicStreamType.BIDIRECTIONAL) {
                                    ch.close();
                                    return;
                                }
                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                                ch.pipeline().addLast(new StreamHandler());
                            }
                        })
                        .build())
                .bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("DoQ listening on {}:{}", bindHost, port);
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

    private final class StreamHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] query = new byte[msg.readableBytes()];
            msg.readBytes(query);
            QuicChannel parent = ((QuicStreamChannel) ctx.channel()).parent();
            SocketAddress remote = parent.remoteSocketAddress();
            InetSocketAddress client = remote instanceof InetSocketAddress inet ? inet : null;
            //noinspection resource
            if (!dns.exchange(query, client, getProtocol(), wire -> ctx.channel().eventLoop().execute(() -> {
                if (!ctx.channel().isActive()) {
                    return;
                }
                ByteBuf out = ctx.alloc().buffer(2 + wire.length);
                out.writeShort(wire.length);
                out.writeBytes(wire);
                ctx.writeAndFlush(out);
            }))) {
                ctx.close();
            }
        }
    }
}
