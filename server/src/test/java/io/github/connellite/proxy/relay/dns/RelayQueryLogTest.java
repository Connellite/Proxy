package io.github.connellite.proxy.relay.dns;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Section;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RelayQueryLogTest {

    @Test
    void keepsTheNewestTwoHundred() {
        RelayQueryLog log = new RelayQueryLog();
        InetSocketAddress client = new InetSocketAddress("203.0.113.8", 53000);
        for (int i = 0; i < RelayQueryLog.LIMIT + 5; i++) {
            log.record(client, "UDP", "n" + i + ".example", "A", "Forwarded", "192.0.2." + (i % 200),
                    System.nanoTime());
        }
        assertThat(log.recent()).hasSize(RelayQueryLog.LIMIT);
        assertThat(log.recent().get(0).name()).isEqualTo("n" + (RelayQueryLog.LIMIT + 4) + ".example");
        assertThat(log.recent().get(0).client()).isEqualTo("203.0.113.8:53000");
        assertThat(log.recent().get(0).protocol()).isEqualTo("UDP");
    }

    @Test
    void forwardedAnswerListsAddressesOrTheCode() throws Exception {
        Name name = Name.fromString("ip.example.ru.");
        Message empty = new Message();
        empty.getHeader().setRcode(Rcode.NXDOMAIN);
        assertThat(RelayQueryLog.answers(empty)).isEqualTo("NXDOMAIN");

        Message answered = new Message();
        answered.addRecord(new ARecord(name, DClass.IN, 30, InetAddress.getByName("192.0.2.1")), Section.ANSWER);
        assertThat(RelayQueryLog.answers(answered)).isEqualTo("192.0.2.1");
    }
}
