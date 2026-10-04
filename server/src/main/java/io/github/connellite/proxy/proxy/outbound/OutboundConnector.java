package io.github.connellite.proxy.proxy.outbound;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.UpstreamSnapshot;
import io.github.connellite.proxy.proxy.ssh.SshUpstreamClient;
import io.github.connellite.proxy.service.SettingsService;
import io.github.connellite.proxy.service.UpstreamProxyService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.NetUtil;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Opens an outbound TCP tunnel to {@code targetHost:targetPort}, either directly
 * or via the currently selected upstream HTTP / HTTPS / SOCKS5 / SSH proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundConnector {

    private final UpstreamProxyService upstreamProxyService;
    private final ProxyProperties properties;
    private final SettingsService settingsService;
    private final SshUpstreamClient sshUpstreamClient;
    private volatile SslContext clientSslContext;

    public void openTunnel(Channel inbound, String targetHost, int targetPort, TunnelCallback callback) {
        Optional<UpstreamSnapshot> selected = upstreamProxyService.currentSelected();
        if (selected.isEmpty()) {
            connectDirect(inbound, targetHost, targetPort, callback);
            return;
        }
        UpstreamSnapshot upstream = selected.get();
        switch (upstream.type()) {
            case SOCKS5 -> connectViaSocks5(inbound, upstream, targetHost, targetPort, callback);
            case SSH -> sshUpstreamClient.openTunnel(inbound, upstream, targetHost, targetPort, callback);
            case HTTPS -> connectViaHttp(inbound, upstream, targetHost, targetPort, callback, true);
            case HTTP -> connectViaHttp(inbound, upstream, targetHost, targetPort, callback, false);
        }
    }

    private void connectDirect(Channel inbound, String host, int port, TunnelCallback callback) {
        Bootstrap bootstrap = newBootstrap(inbound);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                applyOutboundTtl(ch);
                // caller installs handlers after success
            }
        });
        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                callback.onSuccess(future.channel());
            } else {
                callback.onFailure(future.cause() != null
                        ? future.cause()
                        : new IllegalStateException("Direct connect failed"));
            }
        });
    }

    private void connectViaHttp(Channel inbound, UpstreamSnapshot upstream,
                                String targetHost, int targetPort, TunnelCallback callback, boolean tls) {
        SslContext sslContext = null;
        if (tls) {
            try {
                sslContext = clientSslContext();
            } catch (Exception ex) {
                callback.onFailure(ex);
                return;
            }
        }
        String scheme = tls ? "HTTPS" : "HTTP";
        String authority = NetUtil.toSocketAddressString(targetHost, targetPort);
        int outboundTtl = settingsService.get().getOutboundTtl();
        HttpConnectUpstreamHandler handler = tls
                ? new HttpsConnectUpstreamHandler(sslContext, upstream, authority, callback, outboundTtl)
                : new HttpConnectUpstreamHandler(upstream, authority, callback, outboundTtl);
        Bootstrap bootstrap = newBootstrap(inbound);
        bootstrap.handler(handler);
        bootstrap.connect(upstream.host(), upstream.port()).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                callback.onFailure(future.cause() != null
                        ? future.cause()
                        : new IllegalStateException("Unable to connect to upstream " + scheme + " proxy"));
            }
        });
    }

    private void connectViaSocks5(Channel inbound, UpstreamSnapshot upstream,
                                  String targetHost, int targetPort, TunnelCallback callback) {
        boolean wantAuth = upstream.hasAuth();
        Bootstrap bootstrap = newBootstrap(inbound);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                applyOutboundTtl(ch);
                ch.pipeline().addLast(Socks5ClientEncoder.DEFAULT);
                ch.pipeline().addLast(new Socks5InitialResponseDecoder());
                ch.pipeline().addLast(new Socks5UpstreamHandler(upstream, targetHost, targetPort, wantAuth, callback));
            }
        });
        bootstrap.connect(upstream.host(), upstream.port()).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                callback.onFailure(future.cause() != null
                        ? future.cause()
                        : new IllegalStateException("Unable to connect to upstream SOCKS5 proxy"));
            }
        });
    }

    private SslContext clientSslContext() throws Exception {
        SslContext current = clientSslContext;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (clientSslContext == null) {
                clientSslContext = SslContextBuilder.forClient().build();
            }
            return clientSslContext;
        }
    }

    private Bootstrap newBootstrap(Channel inbound) {
        return new Bootstrap()
                .group(inbound.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
    }

    private void applyOutboundTtl(Channel channel) {
        OutboundIpTtl.apply(channel, settingsService.get().getOutboundTtl());
    }
}
