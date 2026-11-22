package io.github.connellite.proxy.relay.tcp;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.model.RelayRouteMode;
import io.github.connellite.proxy.proxy.IdleCloseHandler;
import io.github.connellite.proxy.proxy.QuietCloseHandlerAdapter;
import io.github.connellite.proxy.proxy.RelayHandler;
import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.proxy.outbound.TunnelCallback;
import io.github.connellite.proxy.relay.RelayCatalog;
import io.github.connellite.proxy.relay.RelaySnapshot;
import io.github.connellite.proxy.relay.UpstreamResolver;
import io.github.connellite.proxy.service.UpstreamProxyService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.AbstractSniHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Reads SNI and opens the upstream connection. One instance per accepted socket.
 */
@Slf4j
@RequiredArgsConstructor
final class RelayTcpAcceptHandler extends AbstractSniHandler<InetSocketAddress> {

    private final RelayCatalog catalog;
    private final ProxyProperties properties;
    private final OutboundConnector outboundConnector;
    private final UpstreamProxyService upstreamProxyService;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        // AUTO_READ is off, so nothing reads the ClientHello unless this asks for it.
        ctx.read();
    }

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
        String resolvedHost = host;
        UpstreamResolver.resolve(resolvedHost, snapshot.resolverHost(), snapshot.resolverPort(),
                        properties.getConnectTimeoutMs())
                .whenComplete((address, error) -> ctx.executor().execute(() -> {
                    if (error != null || address == null) {
                        Throwable cause = error == null ? new IllegalStateException("unresolved") : error;
                        log.warn("TCP relay {} not resolved via {}:{}: {}", resolvedHost,
                                snapshot.resolverHost(), snapshot.resolverPort(), cause.toString());
                        promise.setFailure(cause);
                        return;
                    }
                    promise.setSuccess(new InetSocketAddress(address, port));
                }));
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
                log.warn("TCP relay connect {}:{} failed: {}", target.getAddress().getHostAddress(),
                        target.getPort(), connect.cause().toString());
                ctx.close();
                return;
            }
            Channel upstream = connect.channel();
            ctx.pipeline().addLast(new RelayHandler(upstream, null));
            // Removing the hello handler releases the original records, SNI included, to the relay.
            ctx.pipeline().remove(RelayTcpAcceptHandler.this);
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
                ctx.pipeline().remove(RelayTcpAcceptHandler.this);
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
