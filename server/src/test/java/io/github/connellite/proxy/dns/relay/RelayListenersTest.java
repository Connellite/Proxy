package io.github.connellite.proxy.dns.relay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelayListenersTest {

    @Test
    void dohCannotShareTheTcpRelaySocket() {
        assertThat(RelayRouteService.sameEndpoint("0.0.0.0", 443, "0.0.0.0", 443)).isTrue();
        assertThat(RelayRouteService.sameEndpoint("0.0.0.0", 8443, "0.0.0.0", 443)).isFalse();
        assertThat(RelayRouteService.sameEndpoint("127.0.0.1", 443, "0.0.0.0", 443)).isFalse();
    }
}
