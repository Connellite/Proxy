package io.github.connellite.proxy.proxy;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.security.cert.CertificateException;
import javax.net.ssl.SSLException;

@Slf4j
@UtilityClass
final class ProxySsl {

    static SslContext selfSignedServerContext(String commonName) throws CertificateException, SSLException {
        SelfSignedCertificate certificate = new SelfSignedCertificate(commonName);
        log.warn("Using ephemeral self-signed TLS certificate for CN={} (browsers will warn until you trust it)", commonName);
        return SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
    }
}
