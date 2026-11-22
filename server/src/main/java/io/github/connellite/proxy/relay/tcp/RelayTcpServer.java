package io.github.connellite.proxy.relay.tcp;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.proxy.IdleCloseHandler;
import io.github.connellite.proxy.proxy.QuietCloseHandlerAdapter;
import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.relay.RelayCatalog;
import io.github.connellite.proxy.service.UpstreamProxyService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Copies TLS bytes after reading SNI. The ClientHello is not rewritten, so the upstream must be
 * the server for the name the client sent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayTcpServer {

    private final RelayCatalog catalog;
    private final ProxyProperties properties;
    private final OutboundConnector outboundConnector;
    private final UpstreamProxyService upstreamProxyService;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public synchronized boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public synchronized void start(String bindHost, int port) throws InterruptedException {
        stop();
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        serverChannel = new ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.AUTO_READ, false)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS));
                        ch.pipeline().addLast(new IdleCloseHandler());
                        ch.pipeline().addLast(new RelayTcpAcceptHandler(
                                catalog, properties, outboundConnector, upstreamProxyService));
                        QuietCloseHandlerAdapter.installLast(ch.pipeline());
                    }
                })
                .bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("TCP relay listening on {}:{}", bindHost, port);
    }

    public synchronized void stop() {
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            workerGroup = null;
        }
    }
}
