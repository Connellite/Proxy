package io.github.connellite.proxy.proxy.http;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.proxy.IdleCloseHandler;
import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.service.HttpStripHeaderService;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HttpProxyServerInstance implements AutoCloseable {

    private final ProxyAuthService authService;
    private final ProxyMetrics metrics;
    private final ProxyProperties properties;
    private final OutboundConnector outboundConnector;
    private final HttpStripHeaderService stripHeaderService;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public HttpProxyServerInstance(ProxyAuthService authService,
                            ProxyMetrics metrics,
                            ProxyProperties properties,
                            OutboundConnector outboundConnector,
                            HttpStripHeaderService stripHeaderService) {
        this.authService = authService;
        this.metrics = metrics;
        this.properties = properties;
        this.outboundConnector = outboundConnector;
        this.stripHeaderService = stripHeaderService;
    }

    protected String scheme() {
        return "HTTP proxy";
    }

    public synchronized void start(String bindHost, int port) throws InterruptedException {
        stop();
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.AUTO_READ, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        configureTransport(ch);
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS));
                        ch.pipeline().addLast(new IdleCloseHandler());
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(properties.getHttpMaxContentLengthBytes()));
                        ch.pipeline().addLast(new HttpProxyClientHandler(
                                authService, metrics, outboundConnector, stripHeaderService));
                    }
                });
        serverChannel = bootstrap.bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("{} listening on {}:{}", scheme(), bindHost, port);
    }

    /**
     * Plain HTTP adds nothing. HTTPS overrides this and inserts TLS first.
     */
    protected void configureTransport(SocketChannel ch) {
    }

    public synchronized boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    @Override
    public synchronized void close() {
        stop();
    }

    public synchronized void stop() {
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
