package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.service.AuthenticatedSession;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
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
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5Message;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public final class Socks5ProxyServer implements AutoCloseable {

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
                        ch.pipeline().addLast(Socks5ServerEncoder.DEFAULT);
                        ch.pipeline().addLast(new Socks5InitialRequestDecoder());
                        ch.pipeline().addLast(new Socks5InitialHandler());
                    }
                });
        serverChannel = bootstrap.bind(new InetSocketAddress(bindHost, port)).sync().channel();
        log.info("SOCKS5 proxy listening on {}:{}", bindHost, port);
    }

    public synchronized boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public synchronized InetSocketAddress localAddress() {
        if (serverChannel == null) {
            return null;
        }
        return (InetSocketAddress) serverChannel.localAddress();
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

    private final class Socks5InitialHandler extends SimpleChannelInboundHandler<Socks5Message> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Socks5Message msg) {
            if (!(msg instanceof Socks5InitialRequest)) {
                ctx.close();
                return;
            }
            boolean authRequired = authService.isAuthRequired();
            if (authRequired) {
                ctx.pipeline().replace(this, "socks5Auth", new Socks5PasswordHandler());
                ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
            } else {
                if (!metrics.track(ctx.channel(), null)) {
                    ctx.close();
                    return;
                }
                ctx.pipeline().replace(this, "socks5Cmd", new Socks5CommandHandler());
                ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
            }
            ctx.pipeline().remove(Socks5InitialRequestDecoder.class);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("SOCKS5 init error: {}", cause.toString());
            ctx.close();
        }
    }

    private final class Socks5PasswordHandler extends SimpleChannelInboundHandler<Socks5Message> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Socks5Message msg) {
            if (!(msg instanceof Socks5PasswordAuthRequest request)) {
                ctx.close();
                return;
            }
            Optional<AuthenticatedSession> session = authService.authenticate(request.username(), request.password());
            if (session.isEmpty() || !metrics.track(ctx.channel(), session.get())) {
                ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                        .addListener(ChannelFutureListener.CLOSE);
                return;
            }
            ctx.channel().attr(SESSION_KEY).set(session.get());
            ctx.pipeline().replace(this, "socks5Cmd", new Socks5CommandHandler());
            ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
            ctx.pipeline().remove(Socks5PasswordAuthRequestDecoder.class);
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    private final class Socks5CommandHandler extends SimpleChannelInboundHandler<Socks5Message> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Socks5Message msg) {
            if (!(msg instanceof Socks5CommandRequest request)) {
                ctx.close();
                return;
            }
            if (request.type() != Socks5CommandType.CONNECT) {
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                                Socks5CommandStatus.COMMAND_UNSUPPORTED, request.dstAddrType()))
                        .addListener(ChannelFutureListener.CLOSE);
                return;
            }

            Channel inbound = ctx.channel();
            AuthenticatedSession session = inbound.attr(SESSION_KEY).get();
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

            bootstrap.connect(request.dstAddr(), request.dstPort()).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    inbound.writeAndFlush(new DefaultSocks5CommandResponse(
                                    Socks5CommandStatus.FAILURE, request.dstAddrType()))
                            .addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                Channel outbound = future.channel();
                inbound.writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS,
                        Socks5AddressType.IPv4,
                        "0.0.0.0",
                        0)).addListener((ChannelFutureListener) written -> {
                    if (!written.isSuccess()) {
                        outbound.close();
                        inbound.close();
                        return;
                    }
                    if (inbound.pipeline().get(Socks5CommandHandler.class) != null) {
                        inbound.pipeline().remove(Socks5CommandHandler.class);
                    }
                    if (inbound.pipeline().get(Socks5CommandRequestDecoder.class) != null) {
                        inbound.pipeline().remove(Socks5CommandRequestDecoder.class);
                    }
                    if (inbound.pipeline().get(Socks5ServerEncoder.class) != null) {
                        inbound.pipeline().remove(Socks5ServerEncoder.class);
                    }
                    inbound.pipeline().addLast(new RelayHandler(outbound,
                            bytes -> metrics.recordTraffic(userId, bytes, 0)));
                    inbound.config().setAutoRead(true);
                    outbound.config().setAutoRead(true);
                });
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("SOCKS5 command error: {}", cause.toString());
            ctx.close();
        }
    }
}
