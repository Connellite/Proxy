package io.github.connellite.proxy.dns.relay.tcp;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class TcpSessionLogTest {

    @Test
    void keepsTheNewestTwoHundred() {
        TcpSessionLog log = new TcpSessionLog();
        InetSocketAddress client = new InetSocketAddress("203.0.113.8", 44300);
        for (int i = 0; i < TcpSessionLog.LIMIT + 5; i++) {
            log.record(client, "n" + i + ".example", "Relay", System.nanoTime());
        }
        assertThat(log.recent()).hasSize(TcpSessionLog.LIMIT);
        assertThat(log.recent().get(0).hostname()).isEqualTo("n" + (TcpSessionLog.LIMIT + 4) + ".example");
        assertThat(log.recent().get(0).client()).isEqualTo("203.0.113.8:44300");
    }
}
