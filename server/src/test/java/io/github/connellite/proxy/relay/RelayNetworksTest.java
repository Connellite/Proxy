package io.github.connellite.proxy.relay;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelayNetworksTest {

    @Test
    void emptyListAllowsEveryone() throws Exception {
        RelayNetworks networks = RelayNetworks.everyone();
        assertThat(networks.allows(InetAddress.getByName("203.0.113.8"))).isTrue();
        assertThat(networks.allows(null)).isTrue();
    }

    @Test
    void canonicalizesAndMatchesWithoutRewritingFamilies() throws Exception {
        assertThat(RelayNetworks.canonical("203.0.113.10")).isEqualTo("203.0.113.10/32");
        assertThat(RelayNetworks.canonical("2001:db8::1")).isEqualTo("2001:db8::1/128");
        assertThat(RelayNetworks.canonical("203.0.113.10/24")).isEqualTo("203.0.113.0/24");
        assertThatThrownBy(() -> RelayNetworks.canonical("example.com"))
                .isInstanceOf(IllegalArgumentException.class);

        RelayNetworks networks = RelayNetworks.parseAll(List.of("203.0.113.0/24"));
        assertThat(networks.allows(InetAddress.getByName("203.0.113.8"))).isTrue();
        assertThat(networks.allows(InetAddress.getByName("198.51.100.1"))).isFalse();
        assertThat(networks.allows(InetAddress.getByName("2001:db8::1"))).isFalse();
    }
}
