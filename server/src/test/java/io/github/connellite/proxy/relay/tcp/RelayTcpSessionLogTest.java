package io.github.connellite.proxy.relay.tcp;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RelayTcpSessionLogTest {

    @Test
    void keepsTheNewestTwoHundred() {
        RelayTcpSessionLog log = new RelayTcpSessionLog();
        InetSocketAddress client = new InetSocketAddress("203.0.113.8", 44300);
        for (int i = 0; i < RelayTcpSessionLog.LIMIT + 5; i++) {
            log.record(client, "n" + i + ".example", "Relay", System.nanoTime());
        }
        assertThat(log.recent()).hasSize(RelayTcpSessionLog.LIMIT);
        assertThat(log.recent().get(0).hostname()).isEqualTo("n" + (RelayTcpSessionLog.LIMIT + 4) + ".example");
        assertThat(log.recent().get(0).client()).isEqualTo("203.0.113.8:44300");
    }
}
