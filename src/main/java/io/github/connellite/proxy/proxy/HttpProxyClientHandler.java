package io.github.connellite.proxy.proxy;

import com.google.common.net.HostAndPort;
import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
final class HttpProxyClientHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    static final AttributeKey<AuthenticatedSession> SESSION_KEY = AttributeKey.valueOf("proxySession");

    private final ProxyAuthService authService;
    private final ProxyMetrics metrics;
    private final ProxyProperties properties;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        AuthenticatedSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session == null) {
            Optional<AuthenticatedSession> authenticated = authenticate(request);
            if (authService.isHttpAuthRequired()) {
                if (authenticated.isEmpty()) {
                    sendProxyAuthRequired(ctx);
                    return;
                }
                session = authenticated.get();
                if (!metrics.track(ctx.channel(), session)) {
                    sendError(ctx, HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED, "Connection limit reached");
                    return;
                }
                ctx.channel().attr(SESSION_KEY).set(session);
            } else {
                session = authenticated.orElse(null);
                if (!metrics.track(ctx.channel(), session)) {
                    sendError(ctx, HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED, "Connection limit reached");
                    return;
                }
                if (session != null) {
                    ctx.channel().attr(SESSION_KEY).set(session);
                }
            }
        }

        if (HttpMethod.CONNECT.equals(request.method())) {
            handleConnect(ctx, request, session);
        } else {
            handleHttp(ctx, request, session);
        }
    }

    private Optional<AuthenticatedSession> authenticate(FullHttpRequest request) {
        String header = request.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx < 0) {
                return Optional.empty();
            }
            return authService.authenticate(decoded.substring(0, idx), decoded.substring(idx + 1));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request, AuthenticatedSession session) {
        ConnectTarget target;
        try {
            target = parseConnectTarget(request.uri());
        } catch (IllegalArgumentException ex) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid CONNECT target");
            return;
        }
        String host = target.host();
        int port = target.port();

        Channel inbound = ctx.channel();
        Long userId = session == null ? null : session.userId();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(inbound.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new RelayHandler(inbound, bytes -> metrics.recordTraffic(userId, 0, bytes)));
                    }
                });

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel outbound = future.channel();
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        new HttpResponseStatus(200, "Connection Established"),
                        Unpooled.EMPTY_BUFFER,
                        new DefaultHttpHeaders(),
                        new DefaultHttpHeaders());
                inbound.writeAndFlush(response).addListener((ChannelFutureListener) written -> {
                    if (!written.isSuccess()) {
                        outbound.close();
                        inbound.close();
                        return;
                    }
                    inbound.pipeline().remove(HttpObjectAggregator.class);
                    inbound.pipeline().remove(HttpProxyClientHandler.class);
                    if (inbound.pipeline().get(io.netty.handler.codec.http.HttpServerCodec.class) != null) {
                        inbound.pipeline().remove(io.netty.handler.codec.http.HttpServerCodec.class);
                    }
                    inbound.pipeline().addLast(new RelayHandler(outbound, bytes -> metrics.recordTraffic(userId, bytes, 0)));
                    inbound.config().setAutoRead(true);
                    outbound.config().setAutoRead(true);
                });
            } else {
                log.debug("CONNECT failed to {}:{} — {}", host, port, future.cause().toString());
                sendError(ctx, HttpResponseStatus.BAD_GATEWAY, "Unable to connect to target");
            }
        });
    }

    static ConnectTarget parseConnectTarget(String authority) {
        HostAndPort target = HostAndPort.fromString(authority)
                .requireBracketsForIPv6()
                .withDefaultPort(443);
        if (target.getHost().isBlank()) {
            throw new IllegalArgumentException("CONNECT target host is required");
        }
        return new ConnectTarget(target.getHost(), target.getPort());
    }

    private void handleHttp(ChannelHandlerContext ctx, FullHttpRequest request, AuthenticatedSession session) {
        URI uri;
        try {
            uri = URI.create(request.uri());
        } catch (Exception ex) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request URI");
            return;
        }
        if (uri.getHost() == null) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Absolute URI required for proxy requests");
            return;
        }
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path = path + "?" + uri.getRawQuery();
        }

        FullHttpRequest outboundRequest = request.retainedDuplicate();
        outboundRequest.setUri(path);
        outboundRequest.headers().set(HttpHeaderNames.HOST, uri.getPort() > 0 ? host + ":" + port : host);
        outboundRequest.headers().remove(HttpHeaderNames.PROXY_AUTHORIZATION);
        outboundRequest.headers().remove("Proxy-Connection");

        Channel inbound = ctx.channel();
        Long userId = session == null ? null : session.userId();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(inbound.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpRequestEncoder());
                        ch.pipeline().addLast(new HttpResponseDecoder());
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext c, Object msg) {
                                if (msg instanceof ByteBuf buf) {
                                    metrics.recordTraffic(userId, 0, buf.readableBytes());
                                } else if (msg instanceof io.netty.handler.codec.http.HttpContent content) {
                                    metrics.recordTraffic(userId, 0, content.content().readableBytes());
                                }
                                if (inbound.isActive()) {
                                    inbound.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
                                        if (!f.isSuccess()) {
                                            c.close();
                                        }
                                    });
                                } else {
                                    ReferenceCountUtil.release(msg);
                                }
                                if (msg instanceof LastHttpContent) {
                                    c.close();
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext c) {
                                RelayHandler.closeOnFlush(inbound);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext c, Throwable cause) {
                                RelayHandler.closeOnFlush(c.channel());
                            }
                        });
                    }
                });

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                outboundRequest.release();
                sendError(ctx, HttpResponseStatus.BAD_GATEWAY, "Unable to connect to target");
                return;
            }
            Channel outbound = future.channel();
            metrics.recordTraffic(userId, outboundRequest.content().readableBytes(), 0);
            outbound.writeAndFlush(outboundRequest).addListener((ChannelFutureListener) written -> {
                if (!written.isSuccess()) {
                    outbound.close();
                    inbound.close();
                }
            });
            inbound.closeFuture().addListener(f -> outbound.close());
        });
    }

    private void sendProxyAuthRequired(ChannelHandlerContext ctx) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED,
                Unpooled.copiedBuffer("Proxy authentication required", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.PROXY_AUTHENTICATE, "Basic realm=\"Proxy\"");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("HTTP proxy client error: {}", cause.toString());
        ctx.close();
    }

    record ConnectTarget(String host, int port) {
    }
}
