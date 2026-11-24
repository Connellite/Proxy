package io.github.connellite.proxy.service;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class UserConnectionLogTest {

    @Test
    void keepsTheNewestTwoHundred() {
        UserConnectionLog log = new UserConnectionLog();
        InetSocketAddress client = new InetSocketAddress("203.0.113.8", 44300);
        for (int i = 0; i < UserConnectionLog.LIMIT + 5; i++) {
            log.open(client, "n" + i, "HTTP").finish("Connected");
        }
        assertThat(log.recent()).hasSize(UserConnectionLog.LIMIT);
        assertThat(log.recent().get(0).user()).isEqualTo("n" + (UserConnectionLog.LIMIT + 4));
        assertThat(log.recent().get(0).client()).isEqualTo("203.0.113.8:44300");
    }

    @Test
    void finishRecordsOnce() {
        UserConnectionLog log = new UserConnectionLog();
        InetSocketAddress client = new InetSocketAddress("2001:db8::1", 51022);
        UserConnectionLog.Tracker tracker = log.open(client, "alice", "SOCKS5");
        tracker.addUp(12);
        tracker.addDown(34);
        tracker.finish("Connected");
        tracker.addUp(100);
        tracker.finish("Failed");

        assertThat(log.recent()).hasSize(1);
        UserConnectionLog.Entry entry = log.recent().get(0);
        assertThat(entry.user()).isEqualTo("alice");
        assertThat(entry.protocol()).isEqualTo("SOCKS5");
        assertThat(entry.client()).isEqualTo("[2001:db8:0:0:0:0:0:1]:51022");
        assertThat(entry.bytesUp()).isEqualTo(12);
        assertThat(entry.bytesDown()).isEqualTo(34);
        assertThat(entry.outcome()).isEqualTo("Connected");
    }
}
