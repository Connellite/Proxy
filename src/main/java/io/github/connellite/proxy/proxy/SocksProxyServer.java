package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.service.AuthenticatedSession;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public final class SocksProxyServer implements AutoCloseable {

    private static final AttributeKey<AuthenticatedSession> SESSION_KEY = AttributeKey.valueOf("socksSession");

    private final ProxyAuthService authService;
    private final ProxyMetrics metrics;
    private final ProxyProperties properties;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public synchronized void start(String bindHost, int port) throws InterruptedException {
        stop();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS));
                        ch.pipeline().addLast(new IdleCloseHandler());
                        ch.pipeline().addLast(new SocksPortUnificationServerHandler());
                        ch.pipeline().addLast(new SocksClientHandler());
                    }
                });
        serverChannel = bootstrap.bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("SOCKS4/5 proxy listening on {}:{}", bindHost, port);
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

    private final class SocksClientHandler extends SimpleChannelInboundHandler<SocksMessage> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, SocksMessage msg) {
            if (msg instanceof Socks4CommandRequest request) {
                handleSocks4(ctx, request);
            } else if (msg instanceof Socks5InitialRequest) {
                handleSocks5Initial(ctx);
            } else if (msg instanceof Socks5PasswordAuthRequest request) {
                handleSocks5Password(ctx, request);
            } else if (msg instanceof Socks5CommandRequest request) {
                handleSocks5Command(ctx, request);
            } else {
                ctx.close();
            }
        }

        private void handleSocks4(ChannelHandlerContext ctx, Socks4CommandRequest request) {
            if (authService.isAuthRequired()) {
                ctx.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED))
                        .addListener(ChannelFutureListener.CLOSE);
                return;
            }
            if (request.type() != Socks4CommandType.CONNECT) {
                ctx.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED))
                        .addListener(ChannelFutureListener.CLOSE);
                return;
            }
            if (!metrics.track(ctx.channel(), null)) {
                ctx.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED))
                        .addListener(ChannelFutureListener.CLOSE);
                return;
            }
            relay(ctx, request.dstAddr(), request.dstPort(), null, true);
        }

        private void handleSocks5Initial(ChannelHandlerContext ctx) {
            if (authService.isAuthRequired()) {
                ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
            } else {
                if (!metrics.track(ctx.channel(), null)) {
                    ctx.close();
                    return;
                }
                ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
            }
        }

        private void handleSocks5Password(ChannelHandlerContext ctx, Socks5PasswordAuthRequest request) {
            Optional<AuthenticatedSession> session = authService.authenticate(request.username(), request.password());
            if (session.isEmpty() || !metrics.track(ctx.channel(), session.get())) {
                ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                        .addListener(ChannelFutureListener.CLOSE);
                return;
            }
            ctx.channel().attr(SESSION_KEY).set(session.get());
            if (ctx.pipeline().get(Socks5PasswordAuthRequestDecoder.class) != null) {
                ctx.pipeline().remove(Socks5PasswordAuthRequestDecoder.class);
            }
            ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        }

        private void handleSocks5Command(ChannelHandlerContext ctx, Socks5CommandRequest request) {
            if (request.type() != Socks5CommandType.CONNECT) {
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                                Socks5CommandStatus.COMMAND_UNSUPPORTED, request.dstAddrType()))
                        .addListener(ChannelFutureListener.CLOSE);
                return;
            }
            relay(ctx, request.dstAddr(), request.dstPort(), ctx.channel().attr(SESSION_KEY).get(), false);
        }

        private void relay(ChannelHandlerContext ctx, String host, int port,
                           AuthenticatedSession session, boolean socks4) {
            Channel inbound = ctx.channel();
            Long userId = session == null ? null : session.getUserId();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(inbound.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new RelayHandler(inbound,
                                    bytes -> metrics.recordTraffic(userId, 0, bytes)));
                        }
                    });

            bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    Object fail = socks4
                            ? new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED)
                            : new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4);
                    inbound.writeAndFlush(fail).addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                Channel outbound = future.channel();
                Object success = socks4
                        ? new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS)
                        : new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "0.0.0.0", 0);
                inbound.writeAndFlush(success).addListener((ChannelFutureListener) written -> {
                    if (!written.isSuccess()) {
                        outbound.close();
                        inbound.close();
                        return;
                    }
                    stripSocksHandlers(inbound.pipeline());
                    inbound.pipeline().addLast(new RelayHandler(outbound,
                            bytes -> metrics.recordTraffic(userId, bytes, 0)));
                    inbound.config().setAutoRead(true);
                    outbound.config().setAutoRead(true);
                });
            });
        }

        private void stripSocksHandlers(ChannelPipeline pipeline) {
            List<String> remove = new ArrayList<>();
            for (String name : pipeline.names()) {
                ChannelHandler handler = pipeline.get(name);
                if (handler == null) {
                    continue;
                }
                String className = handler.getClass().getName();
                if (handler instanceof SocksClientHandler
                        || handler instanceof SocksPortUnificationServerHandler
                        || className.contains("socksx")
                        || className.contains("Socks4")
                        || className.contains("Socks5")) {
                    remove.add(name);
                }
            }
            for (String name : remove) {
                try {
                    if (pipeline.context(name) != null) {
                        pipeline.remove(name);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("SOCKS client error: {}", cause.toString());
            ctx.close();
        }
    }
}
