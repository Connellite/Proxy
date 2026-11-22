package io.github.connellite.proxy.relay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostPortTest {

    @Test
    void parsesHostPortAndBracketedIpv6() {
        HostPort v4 = HostPort.parse("1.1.1.1:53", 53);
        assertThat(v4.host()).isEqualTo("1.1.1.1");
        assertThat(v4.port()).isEqualTo(53);

        HostPort plain = HostPort.parse("dns.example", 53);
        assertThat(plain.host()).isEqualTo("dns.example");
        assertThat(plain.port()).isEqualTo(53);

        HostPort v6 = HostPort.parse("[2001:db8::1]:5353", 53);
        assertThat(v6.host()).isEqualTo("2001:db8::1");
        assertThat(v6.port()).isEqualTo(5353);

        assertThatThrownBy(() -> HostPort.parse("2001:db8::1", 53))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostPort.parse("dns.example:0", 53))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addressIsALiteralAndNotAnyLocal() {
        assertThat(RelayCatalog.parseAddress("203.0.113.10")).isNotNull();
        assertThat(RelayCatalog.parseAddress("2001:db8::1")).isNotNull();
        assertThat(RelayCatalog.parseAddress("example.com")).isNull();
        assertThat(RelayCatalog.parseAddress("0.0.0.0")).isNull();
        assertThat(RelayCatalog.parseAddress("::")).isNull();
    }
}
