package io.github.connellite.proxy.dns.upstream;

import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import lombok.experimental.UtilityClass;

import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

@UtilityClass
final class QuicUpstream {

    static void requireAvailable(String protocol) {
        if (!Quic.isAvailable()) {
            Throwable cause = Quic.unavailabilityCause();
            throw new IllegalStateException(protocol + " native library is not available", cause);
        }
    }

    static QuicSslContext clientContext(String... applicationProtocols) throws Exception {
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init((KeyStore) null);
        return QuicSslContextBuilder.forClient()
                .trustManager(trust)
                .applicationProtocols(applicationProtocols)
                .build();
    }
}
