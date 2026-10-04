package io.github.connellite.proxy.proxy.socks;

import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.github.connellite.proxy.proxy.RelayHandler;
import io.github.connellite.proxy.proxy.UserTrafficShaping;
import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.proxy.outbound.TunnelCallback;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.SettingsService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
abstract class AbstractSocksClientHandler extends SimpleChannelInboundHandler<SocksMessage> {

    static final AttributeKey<AuthenticatedSession> SESSION_KEY = AttributeKey.valueOf("socksSession");

    final ProxyAuthService authService;
    final ProxyMetrics metrics;
    final SettingsService settingsService;
    final OutboundConnector outboundConnector;

    AbstractSocksClientHandler(ProxyAuthService authService,
                               ProxyMetrics metrics,
                               SettingsService settingsService,
                               OutboundConnector outboundConnector) {
        this.authService = authService;
        this.metrics = metrics;
        this.settingsService = settingsService;
        this.outboundConnector = outboundConnector;
    }

    final void relay(ChannelHandlerContext ctx, String host, int port,
                     AuthenticatedSession session, boolean socks4) {
        Channel inbound = ctx.channel();
        String userId = session == null ? null : session.userId();
        UserTrafficShaping.install(inbound, session);
        outboundConnector.openTunnel(inbound, host, port, new TunnelCallback() {
            @Override
            public void onSuccess(Channel outbound) {
                outbound.pipeline().addLast(new RelayHandler(inbound,
                        bytes -> metrics.recordTraffic(userId, 0, bytes),
                        () -> metrics.allowMoreTraffic(session)));
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
                            bytes -> metrics.recordTraffic(userId, bytes, 0),
                            () -> metrics.allowMoreTraffic(session)));
                    inbound.config().setAutoRead(true);
                    outbound.config().setAutoRead(true);
                });
            }

            @Override
            public void onFailure(Throwable cause) {
                log.debug("SOCKS connect failed to {}:{} — {}", host, port, cause.toString());
                Object fail = socks4
                        ? new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED)
                        : new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4);
                inbound.writeAndFlush(fail).addListener(ChannelFutureListener.CLOSE);
            }
        });
    }

    final void stripSocksHandlers(ChannelPipeline pipeline) {
        List<String> remove = new ArrayList<>();
        for (String name : pipeline.names()) {
            ChannelHandler handler = pipeline.get(name);
            if (handler == null) {
                continue;
            }
            String className = handler.getClass().getName();
            if (handler instanceof AbstractSocksClientHandler
                    || handler instanceof SocksClientHandler
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
