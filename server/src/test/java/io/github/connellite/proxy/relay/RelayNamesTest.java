package io.github.connellite.proxy.relay;

import io.github.connellite.proxy.model.RelayRouteMode;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelayNamesTest {

    @Test
    void exactNamesDoNotMatchEachOther() {
        assertThat(RelayNames.normalizePattern("ip.example.ru")).isEqualTo("ip.example.ru");
        assertThat(RelayNames.normalizePattern("IP.Example.FR.")).isEqualTo("ip.example.fr");
        assertThat(RelayNames.matches("ip.example.ru", "ip.example.fr")).isFalse();
        assertThat(RelayNames.matches("ip.example.ru", "ip.example.ru")).isTrue();
    }

    @Test
    void wildcardDoesNotMatchApexOrADifferentSuffix() {
        assertThat(RelayNames.matches("*.example.com", "example.com")).isFalse();
        assertThat(RelayNames.matches("*.example.com", "notexample.com")).isFalse();
        assertThat(RelayNames.matches("*.example.com", "a.example.com")).isTrue();
        assertThat(RelayNames.matches("*.example.com", "a.b.example.com")).isTrue();
        assertThatThrownBy(() -> RelayNames.normalizePattern("*example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RelayNames.normalizePattern("ex*.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exactRouteBeatsWildcard() throws Exception {
        InetAddress relay = InetAddress.getByName("203.0.113.10");
        RelaySnapshot.Route exact = new RelaySnapshot.Route("ip.example.ru", RelayRouteMode.RELAY, "origin.example", null, null);
        RelaySnapshot.Route mask = new RelaySnapshot.Route("*.example.ru", RelayRouteMode.ADDRESS, null, null, relay);
        RelaySnapshot snapshot = new RelaySnapshot(List.of(mask, exact), relay, "1.1.1.1", 53,
                true, "0.0.0.0", 53, true, "0.0.0.0", 443);
        assertThat(snapshot.match("ip.example.ru.").getMode()).isEqualTo(RelayRouteMode.RELAY);
        assertThat(snapshot.match("other.example.ru").getMode()).isEqualTo(RelayRouteMode.ADDRESS);
    }
}
