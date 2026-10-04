package io.github.connellite.proxy.proxy.http;

import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.service.HttpStripHeaderService;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;

class HttpsProxyClientHandler extends HttpProxyClientHandler {

    HttpsProxyClientHandler(ProxyAuthService authService,
                            ProxyMetrics metrics,
                            OutboundConnector outboundConnector,
                            HttpStripHeaderService stripHeaderService) {
        super(authService, metrics, outboundConnector, stripHeaderService);
    }

    @Override
    protected boolean authRequired() {
        return authService.isHttpsAuthRequired();
    }
}
