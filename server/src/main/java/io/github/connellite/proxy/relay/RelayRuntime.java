package io.github.connellite.proxy.relay;

import io.github.connellite.proxy.proxy.http.ProxyTlsService;
import io.github.connellite.proxy.relay.dns.RelayDnsServer;
import io.github.connellite.proxy.relay.dns.RelayDohServer;
import io.github.connellite.proxy.relay.dns.RelayDoqServer;
import io.github.connellite.proxy.relay.tcp.RelayTcpServer;
import io.github.connellite.proxy.service.SettingsService;
import io.netty.handler.codec.quic.QuicSslContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Starts the DNS and TCP listeners from the current snapshot. A bind failure stays here. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayRuntime {

    private final RelayCatalog catalog;
    private final SettingsService settingsService;
    private final ProxyTlsService tlsService;
    private final RelayDnsServer dnsServer;
    private final RelayTcpServer tcpServer;
    private final RelayDohServer dohServer;
    private final RelayDoqServer doqServer;

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

    public void restartTcp() {
        catalog.reload();
        tcpServer.stop();
        RelaySnapshot snapshot = catalog.current();
        if (!snapshot.tcpEnabled()) {
            log.info("TCP relay disabled");
            return;
        }
        try {
            tcpServer.start(snapshot.tcpBindHost(), snapshot.tcpPort());
        } catch (Exception ex) {
            throw new IllegalStateException(message("TCP relay", ex), ex);
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
        dnsServer.stop();
        tcpServer.stop();
        dohServer.stop();
        doqServer.stop();
    }

    private static String message(String name, Exception ex) {
        String text = ex.getMessage();
        if (text == null || text.isBlank()) {
            text = ex.getClass().getSimpleName();
        }
        return name + ": " + text;
    }
}
