package io.github.connellite.proxy.proxy.http;

import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.service.HttpStripHeaderService;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.UserConnectionLog;

class HttpsProxyClientHandler extends HttpProxyClientHandler {

    HttpsProxyClientHandler(ProxyAuthService authService,
                            ProxyMetrics metrics,
                            OutboundConnector outboundConnector,
                            HttpStripHeaderService stripHeaderService,
                            UserConnectionLog connectionLog) {
        super(authService, metrics, outboundConnector, stripHeaderService, connectionLog);
    }

    @Override
    protected boolean authRequired() {
        return authService.isHttpsAuthRequired();
    }

    @Override
    String getProtocol() {
        return "HTTPS";
    }
}
