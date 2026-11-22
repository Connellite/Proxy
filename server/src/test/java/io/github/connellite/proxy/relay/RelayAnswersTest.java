package io.github.connellite.proxy.relay;

import io.github.connellite.proxy.model.RelayRouteMode;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.Type;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RelayAnswersTest {

    @Test
    void matchedNameHidesRecordsThatCarryTheRealAddress() throws Exception {
        InetAddress relay = InetAddress.getByName("203.0.113.10");
        RelaySnapshot.Route route = new RelaySnapshot.Route("ip.example.ru", RelayRouteMode.RELAY, "origin.example", null, null);
        assertThat(RelayAnswers.decide(route, Type.A, relay)).isEqualTo(RelayAnswers.ANSWER);
        assertThat(RelayAnswers.decide(route, Type.AAAA, relay)).isEqualTo(RelayAnswers.NODATA);
        assertThat(RelayAnswers.decide(route, Type.HTTPS, relay)).isEqualTo(RelayAnswers.NODATA);
        assertThat(RelayAnswers.decide(route, Type.SVCB, relay)).isEqualTo(RelayAnswers.NODATA);
        assertThat(RelayAnswers.decide(route, Type.ANY, relay)).isEqualTo(RelayAnswers.NODATA);
        assertThat(RelayAnswers.decide(route, Type.A, null)).isEqualTo(RelayAnswers.SERVFAIL);
        assertThat(RelayAnswers.decide(null, Type.A, relay)).isEqualTo(RelayAnswers.FORWARD);
    }
}
