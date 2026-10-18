package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.proxy.http.HttpProxyServerInstance;
import io.github.connellite.proxy.proxy.http.HttpsProxyServerInstance;
import io.github.connellite.proxy.proxy.outbound.OutboundConnector;
import io.github.connellite.proxy.proxy.http.ProxyTlsService;
import io.github.connellite.proxy.proxy.socks.SocksProxyServer;
import io.github.connellite.proxy.proxy.ssh.SshProxyServer;
import io.github.connellite.proxy.service.HttpStripHeaderService;
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

import java.util.StringJoiner;

@Slf4j
@Component
@Order(10)
public class ProxyServerManager implements ApplicationRunner {

    private final SettingsService settingsService;
    private final ProxyMetrics metrics;
    private final ProxyTlsService tlsService;
    private final SocksProxyServer socksProxyServer;
    private final SshProxyServer sshProxyServer;
    private final HttpProxyServerInstance httpServer;
    private final HttpsProxyServerInstance httpsServer;

    @Getter
    private volatile String lastError;

    public ProxyServerManager(SettingsService settingsService,
                              ProxyMetrics metrics,
                              ProxyAuthService authService,
                              ProxyProperties properties,
                              ProxyTlsService tlsService,
                              SocksProxyServer socksProxyServer,
                              SshProxyServer sshProxyServer,
                              OutboundConnector outboundConnector,
                              HttpStripHeaderService stripHeaderService) {
        this.settingsService = settingsService;
        this.metrics = metrics;
        this.tlsService = tlsService;
        this.socksProxyServer = socksProxyServer;
        this.sshProxyServer = sshProxyServer;
        this.httpServer = new HttpProxyServerInstance(
                authService, metrics, properties, outboundConnector, stripHeaderService);
        this.httpsServer = new HttpsProxyServerInstance(
                authService, metrics, properties, outboundConnector, stripHeaderService);
    }

    @Override
    public void run(ApplicationArguments args) {
        restart();
    }

    public synchronized void restart() {
        lastError = null;
        AppSettings settings = settingsService.get();
        shutdown();

        StringJoiner errors = new StringJoiner("; ");
        startListener("HTTP proxy", settings.isHttpEnabled(), errors, () ->
                httpServer.start(settings.getHttpBindHost(), settings.getHttpPort())
        );
        startListener("HTTPS proxy", settings.isHttpsEnabled(), errors, () -> {
            SslContext ssl = tlsService.serverContext(settings);
            httpsServer.start(settings.getHttpsBindHost(), settings.getHttpsPort(), ssl);
        });
        startListener("SOCKS4/5 proxy", settings.isSocksEnabled(), errors, () ->
                socksProxyServer.start(settings.getSocksBindHost(), settings.getSocksPort())
        );
        startListener("SSH tunnel proxy", settings.isSshEnabled(), errors, () ->
                sshProxyServer.start(settings.getSshBindHost(), settings.getSshPort())
        );
        if (errors.length() > 0) {
            lastError = errors.toString();
        }
    }

    private void startListener(String name, boolean enabled, StringJoiner errors, ListenerStart start) {
        if (!enabled) {
            log.info("{} disabled", name);
            return;
        }
        try {
            start.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message == null || message.isBlank()) {
                message = ex.getClass().getSimpleName();
            }
            log.error("Failed to start {}", name, ex);
            errors.add(name + ": " + message);
        }
    }

    @FunctionalInterface
    private interface ListenerStart {
        void run() throws Exception;
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

    public boolean isSshRunning() {
        return sshProxyServer.isRunning();
    }

    @PreDestroy
    public void shutdown() {
        httpServer.stop();
        httpsServer.stop();
        socksProxyServer.stop();
        sshProxyServer.stop();
        metrics.resetActiveConnections();
    }
}
