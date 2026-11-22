package io.github.connellite.proxy.relay.tcp;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.model.RelayRouteMode;
import io.github.connellite.proxy.proxy.IdleCloseHandler;
import io.github.connellite.proxy.proxy.QuietCloseHandlerAdapter;
import io.github.connellite.proxy.proxy.RelayHandler;
import io.github.connellite.proxy.relay.RelayCatalog;
import io.github.connellite.proxy.relay.RelaySnapshot;
import io.github.connellite.proxy.relay.UpstreamResolver;
import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.proxy.outbound.TunnelCallback;
import io.github.connellite.proxy.service.UpstreamProxyService;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.AbstractSniHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
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
                        ch.pipeline().addLast(new AcceptHandler());
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

    private final class AcceptHandler extends AbstractSniHandler<InetSocketAddress> {
        @Override
        protected Future<InetSocketAddress> lookup(ChannelHandlerContext ctx, String hostname) {
            //noinspection resource
            Promise<InetSocketAddress> promise = ctx.executor().newPromise();
            InetSocketAddress remote = ctx.channel().remoteAddress() instanceof InetSocketAddress inet ? inet : null;
            RelaySnapshot snapshot = catalog.current();
            if (!snapshot.tcpAllows(remote == null ? null : remote.getAddress())) {
                promise.setFailure(new IllegalStateException("address not allowed"));
                return promise;
            }
            if (hostname == null || hostname.isEmpty()) {
                promise.setFailure(new IllegalStateException("no sni"));
                return promise;
            }
            RelaySnapshot.Route route = snapshot.match(hostname);
            if (route == null || route.getMode() != RelayRouteMode.RELAY) {
                promise.setFailure(new IllegalStateException("no route"));
                return promise;
            }
            int port = route.getUpstreamPort() != null
                    ? route.getUpstreamPort()
                    : ((InetSocketAddress) ctx.channel().localAddress()).getPort();
            String host = route.getUpstreamHost();
            if (host == null || host.isBlank()) {
                host = hostname;
            }
            if (snapshot.tcpUseUpstream() && upstreamProxyService.currentSelected().isPresent()) {
                promise.setSuccess(InetSocketAddress.createUnresolved(host, port));
                return promise;
            }
            UpstreamResolver.resolve(host, snapshot.resolverHost(), snapshot.resolverPort(),
                            properties.getConnectTimeoutMs())
                    .whenComplete((address, error) -> {
                        if (error != null || address == null) {
                            promise.setFailure(error == null ? new IllegalStateException("unresolved") : error);
                        } else {
                            promise.setSuccess(new InetSocketAddress(address, port));
                        }
                    });
            return promise;
        }

        @Override
        protected void onLookupComplete(ChannelHandlerContext ctx, String hostname, Future<InetSocketAddress> future) {
            if (!future.isSuccess()) {
                ctx.close();
                return;
            }
            InetSocketAddress target = future.getNow();
            if (target.isUnresolved()) {
                connectViaUpstream(ctx, target.getHostString(), target.getPort());
                return;
            }
            Bootstrap bootstrap = new Bootstrap().group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.AUTO_READ, false)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(1, properties.getConnectTimeoutMs()))
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new IdleStateHandler(
                                    0, 0, properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS));
                            ch.pipeline().addLast(new IdleCloseHandler());
                            ch.pipeline().addLast(new RelayHandler(ctx.channel(), null));
                            QuietCloseHandlerAdapter.installLast(ch.pipeline());
                        }
                    });
            bootstrap.connect(target).addListener((ChannelFutureListener) connect -> {
                if (!connect.isSuccess()) {
                    ctx.close();
                    return;
                }
                Channel upstream = connect.channel();
                ctx.pipeline().addLast(new RelayHandler(upstream, null));
                // Removing the hello handler releases the original records, SNI included, to the relay.
                ctx.pipeline().remove(AcceptHandler.this);
                upstream.read();
                ctx.read();
            });
        }

        private void connectViaUpstream(ChannelHandlerContext ctx, String host, int port) {
            outboundConnector.openTunnel(ctx.channel(), host, port, new TunnelCallback() {
                @Override
                public void onSuccess(Channel outbound) {
                    outbound.pipeline().addLast(new IdleStateHandler(
                            0, 0, properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS));
                    outbound.pipeline().addLast(new IdleCloseHandler());
                    outbound.pipeline().addLast(new RelayHandler(ctx.channel(), null));
                    QuietCloseHandlerAdapter.installLast(outbound.pipeline());
                    ctx.pipeline().addLast(new RelayHandler(outbound, null));
                    ctx.pipeline().remove(AcceptHandler.this);
                    outbound.read();
                    ctx.read();
                }

                @Override
                public void onFailure(Throwable cause) {
                    ctx.close();
                }
            });
        }
    }
}
