package io.github.connellite.proxy.proxy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpProxyClientHandlerTest {

    @Test
    void parsesConnectTargetWithExplicitPort() {
        HttpProxyClientHandler.ConnectTarget target = HttpProxyClientHandler.parseConnectTarget("example.com:8443");

        assertThat(target.host()).isEqualTo("example.com");
        assertThat(target.port()).isEqualTo(8443);
    }

    @Test
    void usesHttpsPortByDefault() {
        HttpProxyClientHandler.ConnectTarget target = HttpProxyClientHandler.parseConnectTarget("example.com");

        assertThat(target.host()).isEqualTo("example.com");
        assertThat(target.port()).isEqualTo(443);
    }

    @Test
    void parsesBracketedIpv6Target() {
        HttpProxyClientHandler.ConnectTarget target = HttpProxyClientHandler.parseConnectTarget("[::1]:443");

        assertThat(target.host()).isEqualTo("::1");
        assertThat(target.port()).isEqualTo(443);
    }

    @Test
    void rejectsInvalidTargets() {
        assertThrows(IllegalArgumentException.class, () -> HttpProxyClientHandler.parseConnectTarget("example.com:not-a-port"));
        assertThrows(IllegalArgumentException.class, () -> HttpProxyClientHandler.parseConnectTarget("[::1"));
        assertThrows(IllegalArgumentException.class, () -> HttpProxyClientHandler.parseConnectTarget(":443"));
    }
}
