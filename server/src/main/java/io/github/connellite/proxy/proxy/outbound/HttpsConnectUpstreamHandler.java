package io.github.connellite.proxy.proxy.outbound;

import io.github.connellite.proxy.dto.UpstreamSnapshot;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;

class HttpsConnectUpstreamHandler extends HttpConnectUpstreamHandler {

    private final SslContext sslContext;

    HttpsConnectUpstreamHandler(SslContext sslContext, UpstreamSnapshot upstream, String authority,
                                TunnelCallback callback, int outboundTtl) {
        super(upstream, authority, callback, outboundTtl);
        this.sslContext = sslContext;
    }

    @Override
    protected void configureTransport(SocketChannel ch) {
        UpstreamSnapshot proxy = upstream();
        ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), proxy.host(), proxy.port()));
    }

    @Override
    protected String scheme() {
        return "HTTPS";
    }
}
