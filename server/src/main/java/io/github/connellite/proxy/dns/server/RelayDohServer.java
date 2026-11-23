package io.github.connellite.proxy.dns.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/** DNS-over-HTTPS. GET and POST {@code /dns-query} with {@code application/dns-message}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayDohServer implements RelayListener {

    private final RelayDnsServer dns;

    private EventLoopGroup group;
    private Channel server;

    @Override
    public synchronized boolean isRunning() {
        return server != null && server.isActive();
    }

    @Override
    public String getProtocol() {
        return "DoH";
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
                        ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                            @Override
                            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                                    ctx.pipeline().addLast(Http2FrameCodecBuilder.forServer().build());
                                    ctx.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                                        @Override
                                        protected void initChannel(Http2StreamChannel stream) {
                                            stream.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(true));
                                            stream.pipeline().addLast(new HttpObjectAggregator(65535));
                                            stream.pipeline().addLast(new RelayDohHandler(dns, getProtocol()));
                                        }
                                    }));
                                    return;
                                }
                                ctx.pipeline().addLast(new HttpServerCodec());
                                ctx.pipeline().addLast(new HttpObjectAggregator(65535));
                                ctx.pipeline().addLast(new RelayDohHandler(dns, getProtocol()));
                            }
                        });
                    }
                })
                .bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("DoH listening on {}:{}", bindHost, port);
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
