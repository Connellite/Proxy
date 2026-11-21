package io.github.connellite.proxy.relay;

import io.github.connellite.proxy.relay.dns.RelayDnsServer;
import io.github.connellite.proxy.relay.tcp.RelayTcpServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Starts the DNS and TCP listeners from the current snapshot. A bind failure stays here. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayRuntime {

    private final RelayCatalog catalog;
    private final RelayDnsServer dnsServer;
    private final RelayTcpServer tcpServer;

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

    public void stop() {
        dnsServer.stop();
        tcpServer.stop();
    }

    private static String message(String name, Exception ex) {
        String text = ex.getMessage();
        if (text == null || text.isBlank()) {
            text = ex.getClass().getSimpleName();
        }
        return name + ": " + text;
    }
}
