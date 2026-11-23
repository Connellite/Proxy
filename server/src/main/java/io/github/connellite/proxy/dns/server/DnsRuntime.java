package io.github.connellite.proxy.dns.server;

import io.github.connellite.proxy.dns.relay.RelayCatalog;
import io.github.connellite.proxy.dns.relay.RelaySnapshot;
import io.github.connellite.proxy.proxy.http.ProxyTlsService;
import io.github.connellite.proxy.service.SettingsService;
import io.netty.handler.codec.quic.QuicSslContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** Starts DNS listeners from the current snapshot. A bind failure stays here. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DnsRuntime {

    private final RelayCatalog catalog;
    private final SettingsService settingsService;
    private final ProxyTlsService tlsService;
    private final DnsServer dnsServer;
    private final DohServer dohServer;
    private final Doh3Server doh3Server;
    private final DotServer dotServer;
    private final DoqServer doqServer;

    public void restartDns() {
        catalog.reload();
        dnsServer.stop();
        RelaySnapshot snapshot = catalog.current();
        if (!snapshot.dnsEnabled()) {
            log.info("DNS relay disabled");
            return;
        }
        try {
            dnsServer.start(snapshot.dnsBindHost(), snapshot.dnsPort());
        } catch (Exception ex) {
            throw new IllegalStateException(message("DNS relay", ex), ex);
        }
    }

    public void restartDoh() {
        catalog.reload();
        dohServer.stop();
        RelaySnapshot snapshot = catalog.current();
        if (!snapshot.dohEnabled()) {
            log.info("DoH disabled");
            return;
        }
        try {
            dohServer.start(snapshot.dohBindHost(), snapshot.dohPort(), tlsService.dohServerContext(settingsService.get()));
        } catch (Exception ex) {
            throw new IllegalStateException(message("DoH", ex), ex);
        }
    }

    public void restartDoh3() {
        catalog.reload();
        doh3Server.stop();
        RelaySnapshot snapshot = catalog.current();
        if (!snapshot.doh3Enabled()) {
            log.info("DoH3 disabled");
            return;
        }
        try {
            doh3Server.start(snapshot.doh3BindHost(), snapshot.doh3Port(), tlsService.doh3ServerContext(settingsService.get()));
        } catch (Exception ex) {
            throw new IllegalStateException(message("DoH3", ex), ex);
        }
    }

    public void restartDot() {
        catalog.reload();
        dotServer.stop();
        RelaySnapshot snapshot = catalog.current();
        if (!snapshot.dotEnabled()) {
            log.info("DoT disabled");
            return;
        }
        try {
            dotServer.start(snapshot.dotBindHost(), snapshot.dotPort(), tlsService.dotServerContext(settingsService.get()));
        } catch (Exception ex) {
            throw new IllegalStateException(message("DoT", ex), ex);
        }
    }

    public void restartDoq() {
        catalog.reload();
        doqServer.stop();
        RelaySnapshot snapshot = catalog.current();
        if (!snapshot.doqEnabled()) {
            log.info("DoQ disabled");
            return;
        }
        try {
            QuicSslContext ssl = tlsService.doqServerContext(settingsService.get());
            doqServer.start(snapshot.doqBindHost(), snapshot.doqPort(), ssl);
        } catch (Exception ex) {
            throw new IllegalStateException(message("DoQ", ex), ex);
        }
    }

    public void stop() {
        for (Listener listener : List.of(dnsServer, dohServer, doh3Server, dotServer, doqServer)) {
            listener.stop();
        }
    }

    private static String message(String name, Exception ex) {
        String text = ex.getMessage();
        if (text == null || text.isBlank()) {
            text = ex.getClass().getSimpleName();
        }
        return name + ": " + text;
    }
}
