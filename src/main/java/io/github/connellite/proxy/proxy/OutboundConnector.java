package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.UpstreamSnapshot;
import io.github.connellite.proxy.model.UpstreamProxyType;
import io.github.connellite.proxy.service.UpstreamProxyService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

/**
 * Opens an outbound TCP tunnel to {@code targetHost:targetPort}, either directly
 * or via the currently selected upstream HTTP/SOCKS5 proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundConnector {

    private final UpstreamProxyService upstreamProxyService;
    private final ProxyProperties properties;

    public void openTunnel(Channel inbound, String targetHost, int targetPort, TunnelCallback callback) {
        Optional<UpstreamSnapshot> selected = upstreamProxyService.currentSelected();
        if (selected.isEmpty()) {
            connectDirect(inbound, targetHost, targetPort, callback);
            return;
        }
        UpstreamSnapshot upstream = selected.get();
        if (upstream.type() == UpstreamProxyType.SOCKS5) {
            connectViaSocks5(inbound, upstream, targetHost, targetPort, callback);
        } else {
            connectViaHttp(inbound, upstream, targetHost, targetPort, callback);
        }
    }

    private void connectDirect(Channel inbound, String host, int port, TunnelCallback callback) {
        Bootstrap bootstrap = newBootstrap(inbound);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                // empty — caller installs handlers after success
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
                                String targetHost, int targetPort, TunnelCallback callback) {
        String authority = formatAuthority(targetHost, targetPort);
        Bootstrap bootstrap = newBootstrap(inbound);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new HttpClientCodec());
                ch.pipeline().addLast(new HttpObjectAggregator(8192));
                ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                    private boolean finished;

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        DefaultFullHttpRequest connect = new DefaultFullHttpRequest(
                                HttpVersion.HTTP_1_1,
                                HttpMethod.CONNECT,
                                authority,
                                ctx.alloc().buffer(0),
                                new DefaultHttpHeaders(),
                                new DefaultHttpHeaders());
                        connect.headers().set(HttpHeaderNames.HOST, authority);
                        connect.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
                        if (upstream.hasAuth()) {
                            String token = Base64.getEncoder().encodeToString(
                                    (nullToEmpty(upstream.username()) + ":" + nullToEmpty(upstream.password()))
                                            .getBytes(StandardCharsets.UTF_8));
                            connect.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, "Basic " + token);
                        }
                        ctx.writeAndFlush(connect);
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                        if (finished) {
                            return;
                        }
                        finished = true;
                        if (msg.status().code() == 200) {
                            ChannelPipeline pipeline = ctx.pipeline();
                            pipeline.remove(this);
                            removeIfPresent(pipeline, HttpObjectAggregator.class);
                            removeIfPresent(pipeline, HttpClientCodec.class);
                            callback.onSuccess(ctx.channel());
                        } else {
                            IllegalStateException error = new IllegalStateException(
                                    "Upstream HTTP CONNECT failed: " + msg.status());
                            ctx.close();
                            callback.onFailure(error);
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        if (!finished) {
                            finished = true;
                            callback.onFailure(cause);
                        }
                        ctx.close();
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        if (!finished) {
                            finished = true;
                            callback.onFailure(new IllegalStateException("Upstream HTTP connection closed"));
                        }
                    }
                });
            }
        });
        bootstrap.connect(upstream.host(), upstream.port()).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                callback.onFailure(future.cause() != null
                        ? future.cause()
                        : new IllegalStateException("Unable to connect to upstream HTTP proxy"));
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

    private Bootstrap newBootstrap(Channel inbound) {
        return new Bootstrap()
                .group(inbound.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
    }

    private static void removeIfPresent(ChannelPipeline pipeline, Class<? extends ChannelHandler> type) {
        try {
            if (pipeline.get(type) != null) {
                pipeline.remove(type);
            }
        } catch (Exception ignored) {
        }
    }

    private static String formatAuthority(String host, int port) {
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class Socks5UpstreamHandler extends SimpleChannelInboundHandler<Object> {

        private enum Phase { INIT, AUTH, COMMAND, DONE }

        private final UpstreamSnapshot upstream;
        private final String targetHost;
        private final int targetPort;
        private final boolean wantAuth;
        private final TunnelCallback callback;
        private Phase phase = Phase.INIT;
        private boolean finished;

        private Socks5UpstreamHandler(UpstreamSnapshot upstream, String targetHost, int targetPort,
                                      boolean wantAuth, TunnelCallback callback) {
            this.upstream = upstream;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            this.wantAuth = wantAuth;
            this.callback = callback;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            if (wantAuth) {
                ctx.writeAndFlush(new DefaultSocks5InitialRequest(Arrays.asList(
                        Socks5AuthMethod.NO_AUTH, Socks5AuthMethod.PASSWORD)));
            } else {
                ctx.writeAndFlush(new DefaultSocks5InitialRequest(
                        Collections.singletonList(Socks5AuthMethod.NO_AUTH)));
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (finished) {
                return;
            }
            if (phase == Phase.INIT && msg instanceof Socks5InitialResponse response) {
                handleInitial(ctx, response);
            } else if (phase == Phase.AUTH && msg instanceof Socks5PasswordAuthResponse response) {
                handleAuth(ctx, response);
            } else if (phase == Phase.COMMAND && msg instanceof Socks5CommandResponse response) {
                handleCommand(ctx, response);
            } else {
                fail(ctx, new IllegalStateException("Unexpected SOCKS5 response: " + msg.getClass().getSimpleName()));
            }
        }

        private void handleInitial(ChannelHandlerContext ctx, Socks5InitialResponse response) {
            Socks5AuthMethod method = response.authMethod();
            removeIfPresent(ctx.pipeline(), Socks5InitialResponseDecoder.class);
            if (method == Socks5AuthMethod.PASSWORD) {
                if (!wantAuth || StringUtils.isBlank(upstream.username())) {
                    fail(ctx, new IllegalStateException("Upstream SOCKS5 requires username/password"));
                    return;
                }
                phase = Phase.AUTH;
                ctx.pipeline().addBefore(ctx.name(), null, new Socks5PasswordAuthResponseDecoder());
                ctx.writeAndFlush(new DefaultSocks5PasswordAuthRequest(
                        upstream.username(),
                        nullToEmpty(upstream.password())));
            } else if (method == Socks5AuthMethod.NO_AUTH) {
                sendConnect(ctx);
            } else {
                fail(ctx, new IllegalStateException("Upstream SOCKS5 auth method unsupported: " + method));
            }
        }

        private void handleAuth(ChannelHandlerContext ctx, Socks5PasswordAuthResponse response) {
            if (response.status() != Socks5PasswordAuthStatus.SUCCESS) {
                fail(ctx, new IllegalStateException("Upstream SOCKS5 authentication failed"));
                return;
            }
            removeIfPresent(ctx.pipeline(), Socks5PasswordAuthResponseDecoder.class);
            sendConnect(ctx);
        }

        private void sendConnect(ChannelHandlerContext ctx) {
            phase = Phase.COMMAND;
            removeIfPresent(ctx.pipeline(), Socks5InitialResponseDecoder.class);
            ctx.pipeline().addBefore(ctx.name(), null, new Socks5CommandResponseDecoder());
            Socks5AddressType addressType = resolveAddressType(targetHost);
            ctx.writeAndFlush(new DefaultSocks5CommandRequest(
                    Socks5CommandType.CONNECT, addressType, targetHost, targetPort));
        }

        private void handleCommand(ChannelHandlerContext ctx, Socks5CommandResponse response) {
            if (response.status() != Socks5CommandStatus.SUCCESS) {
                fail(ctx, new IllegalStateException("Upstream SOCKS5 CONNECT failed: " + response.status()));
                return;
            }
            finished = true;
            phase = Phase.DONE;
            stripSocksClientHandlers(ctx.pipeline());
            callback.onSuccess(ctx.channel());
        }

        private void fail(ChannelHandlerContext ctx, Throwable cause) {
            if (finished) {
                return;
            }
            finished = true;
            callback.onFailure(cause);
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            fail(ctx, cause);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!finished) {
                fail(ctx, new IllegalStateException("Upstream SOCKS5 connection closed"));
            }
        }

        private static Socks5AddressType resolveAddressType(String host) {
            try {
                if (com.google.common.net.InetAddresses.isInetAddress(host)) {
                    byte[] bytes = com.google.common.net.InetAddresses.forString(host).getAddress();
                    if (bytes.length == 4) {
                        return Socks5AddressType.IPv4;
                    }
                    if (bytes.length == 16) {
                        return Socks5AddressType.IPv6;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // treat as domain
            }
            return Socks5AddressType.DOMAIN;
        }

        private static void stripSocksClientHandlers(ChannelPipeline pipeline) {
            removeIfPresent(pipeline, Socks5CommandResponseDecoder.class);
            removeIfPresent(pipeline, Socks5PasswordAuthResponseDecoder.class);
            removeIfPresent(pipeline, Socks5InitialResponseDecoder.class);
            removeIfPresent(pipeline, Socks5ClientEncoder.class);
            for (String name : pipeline.names()) {
                if (pipeline.get(name) instanceof Socks5UpstreamHandler) {
                    try {
                        pipeline.remove(name);
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        }
    }

    public interface TunnelCallback {
        void onSuccess(Channel outbound);

        void onFailure(Throwable cause);
    }
}
