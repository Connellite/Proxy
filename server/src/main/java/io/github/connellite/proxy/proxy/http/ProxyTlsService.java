package io.github.connellite.proxy.proxy.http;

import io.github.connellite.proxy.dto.TlsStatus;
import io.github.connellite.proxy.dto.AppSettings;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TLS for the HTTPS-to-proxy listener: accept PEM certificate chain / private key
 * (or file paths), validate the pair and server name. No local CA is generated.
 */
@Slf4j
@Component
public class ProxyTlsService {

    private static final int SAN_DNS = 2;
    private static final int SAN_IP = 7;
    private static final DefaultHostnameVerifier HOSTNAME_VERIFIER = new DefaultHostnameVerifier();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public boolean hasCustomMaterial(AppSettings settings) {
        return hasCertificateMaterial(settings) || hasPrivateKeyMaterial(settings);
    }

    public boolean hasCustomPair(AppSettings settings) {
        return hasCertificateMaterial(settings) && hasPrivateKeyMaterial(settings);
    }

    public synchronized SslContext serverContext(AppSettings settings) throws Exception {
        if (!hasCustomPair(settings)) {
            throw new IllegalStateException(
                    "HTTPS requires a certificate chain and private key (PEM content or file paths)");
        }
        TlsMaterial material = loadMaterial(settings);
        ValidatedTls validated = validate(settings, material);
        if (validated.context() == null) {
            throw new IllegalStateException(validated.status().getWarningValidation() != null
                    ? validated.status().getWarningValidation()
                    : "TLS certificate/private key pair is invalid");
        }
        log.info("Using TLS certificate (subject={})", validated.status().getSubject());
        return validated.context();
    }

    public synchronized TlsStatus status(AppSettings settings) {
        TlsStatus status = new TlsStatus();
        status.setPrivateKeySaved(hasInlinePrivateKey(settings));
        if (!hasCustomMaterial(settings)) {
            if (settings != null && settings.isHttpsEnabled()) {
                status.setWarningValidation(
                        "HTTPS is enabled but no certificate chain / private key is configured");
            }
            return status;
        }
        try {
            TlsMaterial material = loadMaterial(settings);
            status = validate(settings, material).status();
            status.setUsingCustomCertificate(hasCustomPair(settings));
            status.setPrivateKeySaved(hasInlinePrivateKey(settings));
            return status;
        } catch (Exception ex) {
            status.setUsingCustomCertificate(hasCustomMaterial(settings));
            status.setWarningValidation(ex.getMessage());
            return status;
        }
    }

    public void validateSettingsOrThrow(AppSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        if (StringUtils.isNotBlank(settings.getHttpsCertificateChain()) && StringUtils.isNotBlank(settings.getHttpsCertificatePath())) {
            throw new IllegalArgumentException("certificate data and file can't be set together");
        }
        if (StringUtils.isNotBlank(settings.getHttpsPrivateKey()) && StringUtils.isNotBlank(settings.getHttpsPrivateKeyPath())) {
            throw new IllegalArgumentException("private key data and file can't be set together");
        }
        if (settings.isHttpsEnabled() || hasCustomMaterial(settings)) {
            if (!hasCustomPair(settings)) {
                throw new IllegalArgumentException(
                        "Both certificate chain and private key are required (PEM content or file paths)");
            }
            TlsStatus status = status(settings);
            if (!status.isValidPair()) {
                throw new IllegalArgumentException(status.getWarningValidation() != null
                        ? status.getWarningValidation()
                        : "TLS certificate/private key pair is invalid");
            }
        }
    }

    private ValidatedTls validate(AppSettings settings, TlsMaterial material) {
        TlsStatus status = new TlsStatus();
        String serverName = StringUtils.trimToNull(settings != null ? settings.getHttpsServerName() : null);
        String warning = null;

        if (material.certificateChain.length > 0) {
            try {
                List<X509Certificate> certs = parseCertificates(material.certificateChain);
                X509Certificate leaf = certs.get(0);
                status.setSubject(leaf.getSubjectX500Principal().getName());
                status.setIssuer(leaf.getIssuerX500Principal().getName());
                status.setNotBefore(leaf.getNotBefore().toInstant());
                status.setNotAfter(leaf.getNotAfter().toInstant());
                status.setDnsNames(collectNames(leaf));
                leaf.checkValidity();
                status.setValidCert(true);

                String chainWarning = validateChain(certs);
                if (chainWarning == null) {
                    status.setValidChain(true);
                } else {
                    warning = chainWarning;
                }
                if (serverName != null && !nameMatches(leaf, serverName)) {
                    String snWarn = "certificate does not match server name \"" + serverName + "\"";
                    warning = warning == null ? snWarn : warning + "; " + snWarn;
                }
            } catch (Exception ex) {
                status.setValidCert(false);
                warning = "certificate: " + ex.getMessage();
            }
        }

        if (material.privateKey.length > 0) {
            try {
                status.setKeyType(detectKeyType(material.privateKey));
                status.setValidKey(true);
            } catch (Exception ex) {
                status.setValidKey(false);
                String keyWarn = "private key: " + ex.getMessage();
                warning = warning == null ? keyWarn : warning + "; " + keyWarn;
            }
        }

        SslContext context = null;
        if (status.isValidCert() && status.isValidKey()) {
            try {
                context = SslContextBuilder.forServer(
                        new ByteArrayInputStream(material.certificateChain),
                        new ByteArrayInputStream(material.privateKey)).build();
                status.setValidPair(true);
            } catch (Exception ex) {
                String pairWarn = "certificate-key pair: " + ex.getMessage();
                warning = warning == null ? pairWarn : warning + "; " + pairWarn;
            }
        }

        status.setWarningValidation(warning);
        return new ValidatedTls(status, context);
    }

    private static TlsMaterial loadMaterial(AppSettings settings) throws Exception {
        return new TlsMaterial(loadCertificateChain(settings), loadPrivateKey(settings));
    }

    private static byte[] loadCertificateChain(AppSettings settings) throws Exception {
        if (StringUtils.isNotBlank(settings.getHttpsCertificatePath())) {
            if (StringUtils.isNotBlank(settings.getHttpsCertificateChain())) {
                throw new IllegalArgumentException("certificate data and file can't be set together");
            }
            Path path = Path.of(settings.getHttpsCertificatePath()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("reading cert file: not found: " + path);
            }
            return Files.readAllBytes(path);
        }
        if (StringUtils.isNotBlank(settings.getHttpsCertificateChain())) {
            return settings.getHttpsCertificateChain().getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    private static byte[] loadPrivateKey(AppSettings settings) throws Exception {
        if (StringUtils.isNotBlank(settings.getHttpsPrivateKeyPath())) {
            if (StringUtils.isNotBlank(settings.getHttpsPrivateKey())) {
                throw new IllegalArgumentException("private key data and file can't be set together");
            }
            Path path = Path.of(settings.getHttpsPrivateKeyPath()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("reading key file: not found: " + path);
            }
            return Files.readAllBytes(path);
        }
        if (StringUtils.isNotBlank(settings.getHttpsPrivateKey())) {
            return settings.getHttpsPrivateKey().getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    private static List<X509Certificate> parseCertificates(byte[] pem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Collection<? extends java.security.cert.Certificate> parsed;
        try (InputStream in = new ByteArrayInputStream(pem)) {
            parsed = factory.generateCertificates(in);
        }
        if (parsed == null || parsed.isEmpty()) {
            throw new IllegalArgumentException("empty certificate");
        }
        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate certificate : parsed) {
            certs.add((X509Certificate) certificate);
        }
        return certs;
    }

    private static String detectKeyType(byte[] pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(new String(pem, StandardCharsets.UTF_8)))) {
            Object object = parser.readObject();
            while (object != null) {
                PrivateKey key = toPrivateKey(object);
                if (key != null) {
                    String algorithm = key.getAlgorithm();
                    return switch (algorithm.toUpperCase(Locale.ROOT)) {
                        case "RSA" -> "RSA";
                        case "EC", "ECDSA" -> "ECDSA";
                        case "ED25519", "EDDSA" -> throw new IllegalArgumentException(
                                "ED25519 keys are not supported by browsers; did you mean to use X25519 for key exchange?");
                        default -> algorithm;
                    };
                }
                object = parser.readObject();
            }
        }
        throw new IllegalArgumentException("no valid keys were found");
    }

    private static PrivateKey toPrivateKey(Object object) throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (object instanceof PEMKeyPair pemKeyPair) {
            return converter.getKeyPair(pemKeyPair).getPrivate();
        }
        if (object instanceof PrivateKeyInfo privateKeyInfo) {
            return converter.getPrivateKey(privateKeyInfo);
        }
        return null;
    }

    /**
     * The chain is valid when the leaf links to a trust anchor: a JVM root, or a
     * self-signed CA included in the uploaded PEM (an internal root is enough).
     */
    private static String validateChain(List<X509Certificate> certs) {
        try {
            Set<TrustAnchor> anchors = new HashSet<>();
            try {
                anchors.addAll(systemTrustAnchors());
            } catch (Exception ignored) {
                // A chain that carries its own root does not need the system store.
            }
            for (X509Certificate cert : certs) {
                if (isSelfSigned(cert)) {
                    anchors.add(new TrustAnchor(cert, null));
                }
            }
            if (anchors.isEmpty()) {
                return "certificate chain has no trust anchor";
            }
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(certs.get(0));
            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, selector);
            params.addCertStore(java.security.cert.CertStore.getInstance(
                    "Collection",
                    new java.security.cert.CollectionCertStoreParameters(certs)));
            params.setRevocationEnabled(false);
            CertPathBuilder.getInstance("PKIX").build(params);
            return null;
        } catch (Exception ex) {
            String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return "certificate chain is not valid: " + detail;
        }
    }

    private static boolean isSelfSigned(X509Certificate cert) {
        if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
            return false;
        }
        try {
            cert.verify(cert.getPublicKey());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static Set<TrustAnchor> systemTrustAnchors() throws Exception {
        Set<TrustAnchor> anchors = fromDefaultTrustManagers();
        if (!anchors.isEmpty()) {
            return anchors;
        }
        // Fallback for environments where TrustManagerFactory has no default store (rare).
        String type = KeyStore.getDefaultType();
        Path[] candidates = new Path[]{
                Path.of(System.getProperty("java.home"), "lib", "security", "cacerts"),
                Path.of(System.getProperty("java.home"), "lib", "security", "jssecacerts")
        };
        char[] password = "changeit".toCharArray();
        for (Path path : candidates) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            KeyStore ks = KeyStore.getInstance(type);
            try (InputStream in = Files.newInputStream(path)) {
                ks.load(in, password);
            }
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (ks.isCertificateEntry(alias)) {
                    java.security.cert.Certificate cert = ks.getCertificate(alias);
                    if (cert instanceof X509Certificate x509Cert) {
                        anchors.add(new TrustAnchor(x509Cert, null));
                    }
                }
            }
            if (!anchors.isEmpty()) {
                return anchors;
            }
        }
        return anchors;
    }

    private static Set<TrustAnchor> fromDefaultTrustManagers() {
        Set<TrustAnchor> anchors = new HashSet<>();
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (TrustManager manager : factory.getTrustManagers()) {
                if (!(manager instanceof X509TrustManager)) {
                    continue;
                }
                for (X509Certificate cert : ((X509TrustManager) manager).getAcceptedIssuers()) {
                    if (cert != null) {
                        anchors.add(new TrustAnchor(cert, null));
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall through to cacerts file lookup.
        }
        return anchors;
    }

    private static List<String> collectNames(X509Certificate cert) throws Exception {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Collection<List<?>> sans = cert.getSubjectAlternativeNames();
        if (sans != null) {
            for (List<?> san : sans) {
                String presented = presentedName(san);
                if (presented != null) {
                    names.add(presented);
                }
            }
        }
        String cn = commonName(cert);
        if (cn != null) {
            names.add(cn);
        }
        return new ArrayList<>(names);
    }

    private static boolean nameMatches(X509Certificate cert, String serverName) {
        try {
            HOSTNAME_VERIFIER.verify(serverName, cert);
            return true;
        } catch (SSLException ex) {
            return false;
        }
    }

    private static String presentedName(List<?> san) {
        if (san == null || san.size() < 2 || !(san.get(0) instanceof Number) || san.get(1) == null) {
            return null;
        }
        int type = ((Number) san.get(0)).intValue();
        Object value = san.get(1);
        if (type == SAN_DNS && value instanceof String dns) {
            return dns.isBlank() ? null : dns.trim();
        }
        if (type == SAN_IP) {
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
            return ipText(value);
        }
        return null;
    }

    private static byte[] ipBytes(Object value) {
        if (value instanceof byte[] raw && (raw.length == 4 || raw.length == 16)) {
            return raw;
        }
        return null;
    }

    private static String ipText(Object value) {
        byte[] bytes = ipBytes(value);
        if (bytes == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String commonName(X509Certificate cert) {
        String dn = cert.getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "CN=", 0, 3)) {
                return trimmed.substring(3).trim();
            }
        }
        return null;
    }

    private static boolean hasCertificateMaterial(AppSettings settings) {
        return settings != null
                && (StringUtils.isNotBlank(settings.getHttpsCertificateChain()) || StringUtils.isNotBlank(settings.getHttpsCertificatePath()));
    }

    private static boolean hasPrivateKeyMaterial(AppSettings settings) {
        return settings != null
                && (StringUtils.isNotBlank(settings.getHttpsPrivateKey()) || StringUtils.isNotBlank(settings.getHttpsPrivateKeyPath()));
    }

    private static boolean hasInlinePrivateKey(AppSettings settings) {
        return settings != null && StringUtils.isNotBlank(settings.getHttpsPrivateKey());
    }

    private record ValidatedTls(TlsStatus status, SslContext context) {
    }

    private record TlsMaterial(byte[] certificateChain, byte[] privateKey) {
        private TlsMaterial(byte[] certificateChain, byte[] privateKey) {
            this.certificateChain = certificateChain != null ? certificateChain : new byte[0];
            this.privateKey = privateKey != null ? privateKey : new byte[0];
        }
    }
}
