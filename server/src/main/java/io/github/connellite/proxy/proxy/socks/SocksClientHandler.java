package io.github.connellite.proxy.proxy.socks;

import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.SettingsService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;

final class SocksClientHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private final ProxyAuthService authService;
    private final ProxyMetrics metrics;
    private final SettingsService settingsService;
    private final OutboundConnector outboundConnector;

    SocksClientHandler(ProxyAuthService authService,
                       ProxyMetrics metrics,
                       SettingsService settingsService,
                       OutboundConnector outboundConnector) {
        this.authService = authService;
        this.metrics = metrics;
        this.settingsService = settingsService;
        this.outboundConnector = outboundConnector;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksMessage msg) {
        AbstractSocksClientHandler next;
        if (msg instanceof Socks4CommandRequest) {
            next = new Socks4ClientHandler(authService, metrics, settingsService, outboundConnector);
        } else if (msg instanceof Socks5InitialRequest) {
            next = new Socks5ClientHandler(authService, metrics, settingsService, outboundConnector);
        } else {
            ctx.close();
            return;
        }
        ctx.pipeline().replace(this, null, next);
        ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
    }
}
