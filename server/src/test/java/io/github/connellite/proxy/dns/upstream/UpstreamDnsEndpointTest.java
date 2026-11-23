package io.github.connellite.proxy.dns.upstream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;

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
