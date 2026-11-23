package io.github.connellite.proxy.dns.upstream;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstreamDnsEndpointTest {

    @Test
    void parsesPlainHttpsTlsH3Quic() {
        UpstreamDnsEndpoint plain = UpstreamDnsEndpoint.parse("1.1.1.1");
        assertThat(plain.kind()).isEqualTo(UpstreamDnsEndpoint.Kind.PLAIN);
        assertThat(plain.host()).isEqualTo("1.1.1.1");
        assertThat(plain.port()).isEqualTo(53);

        UpstreamDnsEndpoint doh = UpstreamDnsEndpoint.parse("https://cloudflare-dns.com");
        assertThat(doh.kind()).isEqualTo(UpstreamDnsEndpoint.Kind.DOH);
        assertThat(doh.uri()).isEqualTo("https://cloudflare-dns.com/dns-query");

        UpstreamDnsEndpoint dot = UpstreamDnsEndpoint.parse("tls://dns.google");
        assertThat(dot.kind()).isEqualTo(UpstreamDnsEndpoint.Kind.DOT);
        assertThat(dot.host()).isEqualTo("dns.google");
        assertThat(dot.port()).isEqualTo(853);

        UpstreamDnsEndpoint doh3 = UpstreamDnsEndpoint.parse("h3://cloudflare-dns.com/dns-query");
        assertThat(doh3.kind()).isEqualTo(UpstreamDnsEndpoint.Kind.DOH3);
        assertThat(doh3.uri()).isEqualTo("https://cloudflare-dns.com/dns-query");

        UpstreamDnsEndpoint doq = UpstreamDnsEndpoint.parse("quic://dns.adguard.com");
        assertThat(doq.kind()).isEqualTo(UpstreamDnsEndpoint.Kind.DOQ);
        assertThat(doq.port()).isEqualTo(853);
    }

    @Test
    void rejectsSdnsAndUnknownScheme() {
        assertThatThrownBy(() -> UpstreamDnsEndpoint.parse("sdns://abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNSCrypt");
        assertThatThrownBy(() -> UpstreamDnsEndpoint.parse("ftp://example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void blankUsesDefault() {
        assertThat(UpstreamDnsEndpoint.parse("")).isEqualTo(UpstreamDnsEndpoint.DEFAULT);
        assertThat(UpstreamDnsEndpoint.parse(null)).isEqualTo(UpstreamDnsEndpoint.DEFAULT);
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
