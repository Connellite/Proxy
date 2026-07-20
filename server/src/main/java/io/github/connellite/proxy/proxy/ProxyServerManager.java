package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.SettingsService;
import io.netty.handler.ssl.SslContext;
#if SPRING_BOOT_3
import jakarta.annotation.PreDestroy;
#else
import javax.annotation.PreDestroy;
#endif
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(10)
public class ProxyServerManager implements ApplicationRunner {

    private final SettingsService settingsService;
    private final ProxyMetrics metrics;
    private final ProxyTlsService tlsService;
    private final SocksProxyServer socksProxyServer;
    private final HttpProxyServerInstance httpServer;
    private final HttpProxyServerInstance httpsServer;

    @Getter
    private volatile String lastError;

    public ProxyServerManager(SettingsService settingsService,
                              ProxyMetrics metrics,
                              ProxyAuthService authService,
                              ProxyProperties properties,
                              ProxyTlsService tlsService,
                              SocksProxyServer socksProxyServer,
                              OutboundConnector outboundConnector) {
        this.settingsService = settingsService;
        this.metrics = metrics;
        this.tlsService = tlsService;
        this.socksProxyServer = socksProxyServer;
        this.httpServer = new HttpProxyServerInstance(authService, metrics, properties, outboundConnector, "HTTP proxy");
        this.httpsServer = new HttpProxyServerInstance(authService, metrics, properties, outboundConnector, "HTTPS proxy");
    }

    @Override
    public void run(ApplicationArguments args) {
        restart();
    }

    public synchronized void restart() {
        lastError = null;
        AppSettings settings = settingsService.get();
        httpServer.stop();
        httpsServer.stop();
        socksProxyServer.stop();
        metrics.resetActiveConnections();
        try {
            if (settings.isHttpEnabled()) {
                httpServer.start(settings.getHttpBindHost(), settings.getHttpPort(), null);
            } else {
                log.info("HTTP proxy disabled");
            }
            if (settings.isHttpsEnabled()) {
                SslContext ssl = tlsService.serverContext(settings);
                httpsServer.start(settings.getHttpsBindHost(), settings.getHttpsPort(), ssl);
            } else {
                log.info("HTTPS proxy disabled");
            }
            if (settings.isSocksEnabled()) {
                socksProxyServer.start(settings.getSocksBindHost(), settings.getSocksPort());
            } else {
                log.info("SOCKS4/5 proxy disabled");
            }
        } catch (Exception ex) {
            lastError = ex.getMessage();
            log.error("Failed to start proxy listeners", ex);
            httpServer.stop();
            httpsServer.stop();
            socksProxyServer.stop();
            metrics.resetActiveConnections();
        }
    }

    public boolean isHttpRunning() {
        return httpServer.isRunning();
    }

    public boolean isHttpsRunning() {
        return httpsServer.isRunning();
    }

    public boolean isSocksRunning() {
        return socksProxyServer.isRunning();
    }

    @PreDestroy
    public void shutdown() {
        httpServer.stop();
        httpsServer.stop();
        socksProxyServer.stop();
        metrics.resetActiveConnections();
    }
}
