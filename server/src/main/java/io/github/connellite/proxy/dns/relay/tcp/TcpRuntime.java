package io.github.connellite.proxy.dns.relay.tcp;

import io.github.connellite.proxy.dns.relay.RelayCatalog;
import io.github.connellite.proxy.dns.relay.RelaySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Starts the TCP relay from the current snapshot. A bind failure stays here. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TcpRuntime {

    private final RelayCatalog catalog;
    private final TcpServer tcpServer;

    public void restart() {
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
