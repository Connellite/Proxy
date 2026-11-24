package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.service.ProxyAllowCatalog;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/** Closes the accepted socket when the client is outside the proxy allowlist. */
public final class AllowListHandler extends ChannelInboundHandlerAdapter {

    private final ProxyAllowCatalog catalog;

    public AllowListHandler(ProxyAllowCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        InetAddress address = null;
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress remote) {
            address = remote.getAddress();
        }
        if (!catalog.allows(address)) {
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }
}
