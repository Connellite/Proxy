package io.github.connellite.proxy.proxy.socks;

import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.SettingsService;
import io.github.connellite.proxy.service.UserConnectionLog;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;

final class Socks4ClientHandler extends AbstractSocksClientHandler {

    Socks4ClientHandler(ProxyAuthService authService,
                        ProxyMetrics metrics,
                        SettingsService settingsService,
                        OutboundConnector outboundConnector,
                        UserConnectionLog connectionLog) {
        super(authService, metrics, settingsService, outboundConnector, connectionLog);
    }

    @Override
    String getProtocol() {
        return "SOCKS4";
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksMessage msg) {
        if (!(msg instanceof Socks4CommandRequest request)) {
            ctx.close();
            return;
        }
        if (authService.isSocksAuthRequired() || request.type() != Socks4CommandType.CONNECT) {
            ctx.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (!metrics.track(ctx.channel(), null)) {
            connectionLog.open(remote(ctx.channel()), null, getProtocol()).finish("Limit");
            ctx.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }
        relay(ctx, request.dstAddr(), request.dstPort(), null, true);
    }
}
