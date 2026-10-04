package io.github.connellite.proxy.proxy.http;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.service.HttpStripHeaderService;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;

public class HttpsProxyServerInstance extends HttpProxyServerInstance {

    private SslContext sslContext;

    public HttpsProxyServerInstance(ProxyAuthService authService,
                                    ProxyMetrics metrics,
                                    ProxyProperties properties,
                                    OutboundConnector outboundConnector,
                                    HttpStripHeaderService stripHeaderService) {
        super(authService, metrics, properties, outboundConnector, stripHeaderService);
    }

    @Override
    protected String scheme() {
        return "HTTPS proxy";
    }

    public synchronized void start(String bindHost, int port, SslContext sslContext) throws InterruptedException {
        if (sslContext == null) {
            throw new IllegalStateException("HTTPS proxy requires a TLS context");
        }
        this.sslContext = sslContext;
        super.start(bindHost, port);
    }

    @Override
    protected void configureTransport(SocketChannel ch) {
        ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
    }
}
