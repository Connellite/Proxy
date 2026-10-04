package io.github.connellite.proxy.proxy.socks;

import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.github.connellite.proxy.proxy.UserTrafficShaping;
import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.proxy.outbound.OutboundIpTtl;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.SettingsService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.SocksMessage;
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
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Optional;

@Slf4j
final class Socks5ClientHandler extends AbstractSocksClientHandler {

    Socks5ClientHandler(ProxyAuthService authService,
                        ProxyMetrics metrics,
                        SettingsService settingsService,
                        OutboundConnector outboundConnector) {
        super(authService, metrics, settingsService, outboundConnector);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksMessage msg) {
        if (msg instanceof Socks5InitialRequest) {
            handleInitial(ctx);
        } else if (msg instanceof Socks5PasswordAuthRequest request) {
            handlePassword(ctx, request);
        } else if (msg instanceof Socks5CommandRequest request) {
            handleCommand(ctx, request);
        } else {
            ctx.close();
        }
    }

    private void handleInitial(ChannelHandlerContext ctx) {
        if (authService.isSocksAuthRequired()) {
            ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
            return;
        }
        if (!metrics.track(ctx.channel(), null)) {
            ctx.close();
            return;
        }
        ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
        ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
    }

    private void handlePassword(ChannelHandlerContext ctx, Socks5PasswordAuthRequest request) {
        Optional<AuthenticatedSession> session = authService.authenticate(request.username(), request.password());
        if (session.isEmpty() || !metrics.track(ctx.channel(), session.get())) {
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        ctx.channel().attr(SESSION_KEY).set(session.get());
        UserTrafficShaping.install(ctx.channel(), session.get());
        if (ctx.pipeline().get(Socks5PasswordAuthRequestDecoder.class) != null) {
            ctx.pipeline().remove(Socks5PasswordAuthRequestDecoder.class);
        }
        ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
        ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
    }

    private void handleCommand(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        if (request.type() == Socks5CommandType.CONNECT) {
            relay(ctx, request.dstAddr(), request.dstPort(), ctx.channel().attr(SESSION_KEY).get(), false);
            return;
        }
        if (request.type() == Socks5CommandType.UDP_ASSOCIATE && authService.isSocksUdpEnabled()) {
            associateUdp(ctx, request);
            return;
        }
        ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.COMMAND_UNSUPPORTED, request.dstAddrType()))
                .addListener(ChannelFutureListener.CLOSE);
    }

    private void associateUdp(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        Channel inbound = ctx.channel();
        AuthenticatedSession session = inbound.attr(SESSION_KEY).get();
        InetSocketAddress localTcp = (InetSocketAddress) inbound.localAddress();
        if (localTcp == null || localTcp.getAddress() == null) {
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                            Socks5CommandStatus.FAILURE, request.dstAddrType()))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }

        Bootstrap udpBootstrap = new Bootstrap();
        udpBootstrap.group(inbound.eventLoop())
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        OutboundIpTtl.apply(ch, settingsService.get().getOutboundTtl());
                        ch.pipeline().addLast(new Socks5UdpRelayHandler(inbound, session, metrics));
                    }
                });

        udpBootstrap.bind(new InetSocketAddress(localTcp.getAddress(), 0)).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.debug("UDP ASSOCIATE bind failed: {}", future.cause().toString());
                inbound.writeAndFlush(new DefaultSocks5CommandResponse(
                                Socks5CommandStatus.FAILURE, request.dstAddrType()))
                        .addListener(ChannelFutureListener.CLOSE);
                return;
            }
            Channel udpChannel = future.channel();
            InetSocketAddress bound = (InetSocketAddress) udpChannel.localAddress();
            Socks5AddressType bndType = Socks5UdpMessages.addressType(bound.getAddress());
            String bndHost = Socks5UdpMessages.normalizeHost(bound.getAddress());
            inbound.writeAndFlush(new DefaultSocks5CommandResponse(
                            Socks5CommandStatus.SUCCESS, bndType, bndHost, bound.getPort()))
                    .addListener((ChannelFutureListener) written -> {
                        if (!written.isSuccess()) {
                            udpChannel.close();
                            inbound.close();
                            return;
                        }
                        stripSocksHandlers(inbound.pipeline());
                        inbound.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelInactive(ChannelHandlerContext c) {
                                udpChannel.close();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext c, Throwable cause) {
                                udpChannel.close();
                                c.close();
                            }
                        });
                    });
        });
    }
}
