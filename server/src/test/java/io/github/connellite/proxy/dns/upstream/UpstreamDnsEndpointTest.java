package io.github.connellite.proxy.dns.upstream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;
import org.xbill.DNS.WireParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cases mirrored from AdGuard dnsproxy {@code TestAddressToUpstream} /
 * {@code TestAddressToUpstream_bads}.
 *
 * <p>https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream_internal_test.go#L239-L370
 */
class UpstreamDnsEndpointTest {

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream_internal_test.go#L248-L310
     * — assert {@code Address()} equivalent via {@link UpstreamDnsEndpoint#address()}.
     */
    @ParameterizedTest
    @CsvSource({
            "1.1.1.1, 1.1.1.1:53",
            "1.1.1.1:5353, 1.1.1.1:5353",
            "one:5353, one:5353",
            "one.one.one.one, one.one.one.one:53",
            "udp://one.one.one.one, one.one.one.one:53",
            "tcp://one.one.one.one, tcp://one.one.one.one:53",
            "tls://one.one.one.one, tls://one.one.one.one:853",
            "https://one.one.one.one, https://one.one.one.one:443",
            "h3://one.one.one.one, https://one.one.one.one:443",
            "::ffff:1.1.1.1, '[::ffff:1.1.1.1]:53'",
            "https://[2606:4700:4700::1111]/dns-query, 'https://[2606:4700:4700::1111]:443/dns-query'",
            "https://[2606:4700:4700::1111]:443/dns-query, 'https://[2606:4700:4700::1111]:443/dns-query'",
    })
    void addressMatchesAdguardAddressToUpstream(String addr, String want) {
        assertThat(UpstreamDnsEndpoint.parse(addr).address()).isEqualTo(want);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream_internal_test.go#L313-L370
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "asdf://1.1.1.1",
            "12345.1.1.1:1234567",
            ":1234567",
            "host:",
            ":53",
            "!!!",
            "123",
            "tcp://12345.1.1.1:1234567",
            "tcp://:1234567",
            "tcp://host:",
            "tcp://:53",
            "tcp://!!!",
            "tcp://123",
    })
    void rejectsAdguardAddressToUpstreamBads(String addr) {
        assertThatThrownBy(() -> UpstreamDnsEndpoint.parse(addr))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Beyond the AdGuard table: url.Parse / net.SplitHostPort / ValidateDomainName semantics.
     */
    @ParameterizedTest
    @CsvSource({
            "'[::1]', '[::1]:53'",
            "'[::1]:5353', '[::1]:5353'",
            "TLS://one.one.one.one, tls://one.one.one.one:853",
            "tls://my_dns.lan, tls://my_dns.lan:853",
            "quic://dns.adguard.com, quic://dns.adguard.com:853",
            "tcp://user@one.one.one.one, tcp://user@one.one.one.one:53",
            "https://dns.example/a%2Fb?x=%20, https://dns.example:443/a%2Fb?x=%20",
            "h3://dns.google/dns-query, https://dns.google:443/dns-query",
            "пример.рф, пример.рф:53",
    })
    void addressFollowsGoUrlSemantics(String addr, String want) {
        assertThat(UpstreamDnsEndpoint.parse(addr).address()).isEqualTo(want);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.1.1.1:+53",
            "1.1.1.1:-1",
            "1.1.1.1:65536",
            "dns.my_lan",
            "dns.-lan",
            "dns.lan-",
            "example.com.",
            "a..example.com",
            "tls://[::1]:",
            "https://dns example/dns-query",
    })
    void rejectsInvalidAddresses(String addr) {
        assertThatThrownBy(() -> UpstreamDnsEndpoint.parse(addr))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endpointFieldsForClients() {
        UpstreamDnsEndpoint dot = UpstreamDnsEndpoint.parse("tls://[2606:4700:4700::1111]");
        assertThat(dot.host()).isEqualTo("2606:4700:4700::1111");
        assertThat(dot.port()).isEqualTo(853);

        UpstreamDnsEndpoint doh = UpstreamDnsEndpoint.parse("https://user@dns.google:8443/dns-query#frag");
        assertThat(doh.host()).isEqualTo("dns.google");
        assertThat(doh.port()).isEqualTo(8443);
        assertThat(doh.uri()).isEqualTo("https://dns.google:8443/dns-query");
        assertThat(doh.address()).isEqualTo("https://user@dns.google:8443/dns-query#frag");
    }

    @Test
    void formatListenerOmitsDefaultPorts() {
        assertThat(UpstreamDnsEndpoint.formatListener(UpstreamDnsEndpoint.Kind.PLAIN, "10.0.0.1", 53))
                .isEqualTo("10.0.0.1");
        assertThat(UpstreamDnsEndpoint.formatListener(UpstreamDnsEndpoint.Kind.DOH, "::1", 8443))
                .isEqualTo("https://[::1]:8443/dns-query");
        assertThat(UpstreamDnsEndpoint.formatListener(UpstreamDnsEndpoint.Kind.DOQ, "dns.lan", 853))
                .isEqualTo("quic://dns.lan");
        assertThat(UpstreamDnsEndpoint.formatListener(UpstreamDnsEndpoint.Kind.DOT, " ", 853)).isEmpty();
    }

    @Test
    void responseRestoresIdAndChecksQuestion() throws Exception {
        Message query = Message.newQuery(Record.newRecord(Name.fromString("Example.com."), Type.A, DClass.IN));
        int originalId = query.getHeader().getID();

        Message reply = AbstractUpstreamDnsResolver.withZeroId(query);
        reply.getHeader().setFlag(Flags.QR);
        Message parsed = AbstractUpstreamDnsResolver.response(query, reply.toWire(), 0);
        assertThat(parsed.getHeader().getID()).isEqualTo(originalId);

        Message wrongType = Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.AAAA, DClass.IN));
        wrongType.getHeader().setID(0);
        assertThatThrownBy(() -> AbstractUpstreamDnsResolver.response(query, wrongType.toWire(), 0))
                .isInstanceOf(WireParseException.class);

        assertThatThrownBy(() -> AbstractUpstreamDnsResolver.response(query, reply.toWire(), 1))
                .isInstanceOf(WireParseException.class);
    }

    @Test
    void kindsForSchemes() {
        assertThat(UpstreamDnsEndpoint.parse("udp://one.one.one.one").kind())
                .isEqualTo(UpstreamDnsEndpoint.Kind.PLAIN);
        assertThat(UpstreamDnsEndpoint.parse("tcp://one.one.one.one").kind())
                .isEqualTo(UpstreamDnsEndpoint.Kind.TCP);
        assertThat(UpstreamDnsEndpoint.parse("https://one.one.one.one").kind())
                .isEqualTo(UpstreamDnsEndpoint.Kind.DOH);
        assertThat(UpstreamDnsEndpoint.parse("h3://one.one.one.one").kind())
                .isEqualTo(UpstreamDnsEndpoint.Kind.DOH3);
        assertThat(UpstreamDnsEndpoint.parse("tls://one.one.one.one").kind())
                .isEqualTo(UpstreamDnsEndpoint.Kind.DOT);
        assertThat(UpstreamDnsEndpoint.parse("quic://dns.adguard.com").kind())
                .isEqualTo(UpstreamDnsEndpoint.Kind.DOQ);
    }

    @Test
    void rejectsSdns() {
        assertThatThrownBy(() -> UpstreamDnsEndpoint.parse("sdns://abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNSCrypt");
    }

    @Test
    void blankUsesDefault() {
        assertThat(UpstreamDnsEndpoint.parse("")).isEqualTo(UpstreamDnsEndpoint.DEFAULT);
        assertThat(UpstreamDnsEndpoint.parse(null)).isEqualTo(UpstreamDnsEndpoint.DEFAULT);
        assertThat(UpstreamDnsEndpoint.DEFAULT.address()).isEqualTo("1.1.1.1:53");
    }

    @Test
    void createReturnsMatchingResolverKinds() throws Exception {
        Resolver plain = UpstreamResolver.create(UpstreamDnsEndpoint.parse("8.8.8.8:53"), 1000);
        assertThat(plain).isInstanceOf(SimpleResolver.class);

        Resolver doh = UpstreamResolver.create(
                UpstreamDnsEndpoint.parse("https://dns.google/dns-query"), 1000);
        assertThat(doh).isInstanceOf(DohResolver.class);

        Resolver dot = UpstreamResolver.create(UpstreamDnsEndpoint.parse("tls://1.1.1.1"), 1000);
        assertThat(dot).isInstanceOf(DotResolver.class);

        Resolver doh3 = UpstreamResolver.create(
                UpstreamDnsEndpoint.parse("h3://cloudflare-dns.com/dns-query"), 1000);
        assertThat(doh3).isInstanceOf(Doh3Resolver.class);

        Resolver doq = UpstreamResolver.create(UpstreamDnsEndpoint.parse("quic://dns.adguard.com"), 1000);
        assertThat(doq).isInstanceOf(DoqResolver.class);
    }
}
