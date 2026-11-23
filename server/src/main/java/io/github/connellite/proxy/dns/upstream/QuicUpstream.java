package io.github.connellite.proxy.dns.upstream;

import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicSslEngine;
import lombok.experimental.UtilityClass;

import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.function.Function;

@UtilityClass
final class QuicUpstream {

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/doq.go#L57-L60
     */
    private static final String[] DOQ_ALPN = {"doq", "doq-i00", "dq", "doq-i02"};

    static void requireAvailable(String protocol) {
        if (!Quic.isAvailable()) {
            Throwable cause = Quic.unavailabilityCause();
            throw new IllegalStateException(protocol + " native library is not available", cause);
        }
    }

    static QuicSslContext doqContext() {
        return Contexts.DOQ;
    }

    static QuicSslContext doh3Context() {
        return Contexts.DOH3;
    }

    /**
     * The codec default creates engines without a peer host: no SNI and no hostname check.
     */
    static Function<QuicChannel, QuicSslEngine> engineFor(QuicSslContext ssl, String host, int port) {
        return channel -> ssl.newEngine(channel.alloc(), host, port);
    }

    /**
     * QUIC needs a resolved address; {@link InetSocketAddress#getHostString()} keeps {@code host} for SNI.
     */
    static InetSocketAddress resolve(String host, int port) throws UnknownHostException {
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.isUnresolved()) {
            throw new UnknownHostException(host);
        }
        return address;
    }

    private static QuicSslContext clientContext(String... applicationProtocols) {
        try {
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init((KeyStore) null);
            return QuicSslContextBuilder.forClient()
                    .trustManager(trust)
                    .endpointIdentificationAlgorithm("HTTPS")
                    .applicationProtocols(applicationProtocols)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create QUIC client TLS context", ex);
        }
    }

    private static final class Contexts {
        private static final QuicSslContext DOQ = clientContext(DOQ_ALPN);
        private static final QuicSslContext DOH3 = clientContext(Http3.supportedApplicationProtocols());
    }
}
