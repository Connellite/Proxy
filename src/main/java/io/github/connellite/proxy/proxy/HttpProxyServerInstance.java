package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
final class HttpProxyServerInstance implements AutoCloseable {

    private final ProxyAuthService authService;
    private final ProxyMetrics metrics;
    private final ProxyProperties properties;
    private final String label;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    HttpProxyServerInstance(ProxyAuthService authService,
                            ProxyMetrics metrics,
                            ProxyProperties properties,
                            String label) {
        this.authService = authService;
        this.metrics = metrics;
        this.properties = properties;
        this.label = label;
    }

    synchronized void start(String bindHost, int port, SslContext sslContext) throws InterruptedException {
        stop();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.AUTO_READ, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (sslContext != null) {
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                        }
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS));
                        ch.pipeline().addLast(new IdleCloseHandler());
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(8 * 1024 * 1024));
                        ch.pipeline().addLast(new HttpProxyClientHandler(authService, metrics, properties));
                    }
                });
        serverChannel = bootstrap.bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("{} listening on {}:{}", label, bindHost, port);
    }

    synchronized boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    @Override
    public synchronized void close() {
        stop();
    }

    synchronized void stop() {
        if (serverChannel != null) {
            try {
                serverChannel.close().syncUninterruptibly();
            } catch (Exception ignored) {
            }
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
            workerGroup = null;
        }
    }
}
