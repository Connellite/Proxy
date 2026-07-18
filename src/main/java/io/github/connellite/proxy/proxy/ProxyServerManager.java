package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.domain.AppSettings;
import io.github.connellite.proxy.service.SettingsService;
#if SPRING_BOOT_3
import jakarta.annotation.PreDestroy;
#else
import javax.annotation.PreDestroy;
#endif
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class ProxyServerManager implements ApplicationRunner {

    private final SettingsService settingsService;
    private final HttpProxyServer httpProxyServer;
    private final Socks5ProxyServer socks5ProxyServer;

    @Getter
    private volatile String lastError;

    @Override
    public void run(ApplicationArguments args) {
        restart();
    }

    public synchronized void restart() {
        lastError = null;
        AppSettings settings = settingsService.get();
        httpProxyServer.stop();
        socks5ProxyServer.stop();
        try {
            if (settings.isHttpEnabled()) {
                httpProxyServer.start(settings.getHttpBindHost(), settings.getHttpPort());
            } else {
                log.info("HTTP proxy disabled");
            }
            if (settings.isSocksEnabled()) {
                socks5ProxyServer.start(settings.getSocksBindHost(), settings.getSocksPort());
            } else {
                log.info("SOCKS5 proxy disabled");
            }
        } catch (Exception ex) {
            lastError = ex.getMessage();
            log.error("Failed to start proxy listeners", ex);
            httpProxyServer.stop();
            socks5ProxyServer.stop();
        }
    }

    public boolean isHttpRunning() {
        return httpProxyServer.isRunning();
    }

    public boolean isSocksRunning() {
        return socks5ProxyServer.isRunning();
    }

    @PreDestroy
    public void shutdown() {
        httpProxyServer.stop();
        socks5ProxyServer.stop();
    }
}
