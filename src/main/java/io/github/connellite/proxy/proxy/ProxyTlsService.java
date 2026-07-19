package io.github.connellite.proxy.proxy;

import io.github.connellite.proxy.dto.TlsStatus;
import io.github.connellite.proxy.model.AppSettings;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
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
        TlsStatus status = validate(settings, material);
        if (!status.isValidPair()) {
            throw new IllegalStateException(status.getWarningValidation() != null
                    ? status.getWarningValidation()
                    : "TLS certificate/private key pair is invalid");
        }
        log.info("Using TLS certificate (subject={})", status.getSubject());
        return SslContextBuilder.forServer(
                new ByteArrayInputStream(material.certificateChain),
                new ByteArrayInputStream(material.privateKey)).build();
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
            status = validate(settings, material);
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
        if (hasText(settings.getHttpsCertificateChain()) && hasText(settings.getHttpsCertificatePath())) {
            throw new IllegalArgumentException("certificate data and file can't be set together");
        }
        if (hasText(settings.getHttpsPrivateKey()) && hasText(settings.getHttpsPrivateKeyPath())) {
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

    private TlsStatus validate(AppSettings settings, TlsMaterial material) {
        TlsStatus status = new TlsStatus();
        String serverName = blankToNull(settings != null ? settings.getHttpsServerName() : null);
        String warning = null;

        if (material.certificateChain.length > 0) {
            try {
                List<X509Certificate> certs = parseCertificates(material.certificateChain);
                status.setValidCert(true);
                X509Certificate leaf = certs.get(0);
                status.setSubject(leaf.getSubjectX500Principal().getName());
                status.setIssuer(leaf.getIssuerX500Principal().getName());
                status.setNotBefore(leaf.getNotBefore().toInstant());
                status.setNotAfter(leaf.getNotAfter().toInstant());
                status.setDnsNames(collectNames(leaf));

                String chainWarning = validatePublicTrust(certs);
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

        if (status.isValidCert() && status.isValidKey()) {
            try {
                SslContextBuilder.forServer(
                        new ByteArrayInputStream(material.certificateChain),
                        new ByteArrayInputStream(material.privateKey)).build();
                status.setValidPair(true);
            } catch (Exception ex) {
                status.setValidPair(false);
                String pairWarn = "certificate-key pair: " + ex.getMessage();
                warning = warning == null ? pairWarn : warning + "; " + pairWarn;
            }
        }

        status.setWarningValidation(warning);
        return status;
    }

    private static TlsMaterial loadMaterial(AppSettings settings) throws Exception {
        return new TlsMaterial(loadCertificateChain(settings), loadPrivateKey(settings));
    }

    private static byte[] loadCertificateChain(AppSettings settings) throws Exception {
        if (hasText(settings.getHttpsCertificatePath())) {
            if (hasText(settings.getHttpsCertificateChain())) {
                throw new IllegalArgumentException("certificate data and file can't be set together");
            }
            Path path = Path.of(settings.getHttpsCertificatePath()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("reading cert file: not found: " + path);
            }
            return Files.readAllBytes(path);
        }
        if (hasText(settings.getHttpsCertificateChain())) {
            return settings.getHttpsCertificateChain().getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    private static byte[] loadPrivateKey(AppSettings settings) throws Exception {
        if (hasText(settings.getHttpsPrivateKeyPath())) {
            if (hasText(settings.getHttpsPrivateKey())) {
                throw new IllegalArgumentException("private key data and file can't be set together");
            }
            Path path = Path.of(settings.getHttpsPrivateKeyPath()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("reading key file: not found: " + path);
            }
            return Files.readAllBytes(path);
        }
        if (hasText(settings.getHttpsPrivateKey())) {
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
                    if ("RSA".equalsIgnoreCase(algorithm)) {
                        return "RSA";
                    }
                    if ("EC".equalsIgnoreCase(algorithm) || "ECDSA".equalsIgnoreCase(algorithm)) {
                        return "ECDSA";
                    }
                    if ("Ed25519".equalsIgnoreCase(algorithm) || "EdDSA".equalsIgnoreCase(algorithm)) {
                        throw new IllegalArgumentException(
                                "ED25519 keys are not supported by browsers; "
                                        + "did you mean to use X25519 for key exchange?");
                    }
                    return algorithm;
                }
                object = parser.readObject();
            }
        }
        throw new IllegalArgumentException("no valid keys were found");
    }

    private static PrivateKey toPrivateKey(Object object) throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (object instanceof PEMKeyPair) {
            return converter.getKeyPair((PEMKeyPair) object).getPrivate();
        }
        if (object instanceof PrivateKeyInfo) {
            return converter.getPrivateKey((PrivateKeyInfo) object);
        }
        return null;
    }

    private static String validatePublicTrust(List<X509Certificate> certs) {
        try {
            X509Certificate leaf = certs.get(0);
            Set<TrustAnchor> anchors = systemTrustAnchors();
            if (anchors.isEmpty()) {
                return "could not load system trust store";
            }
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(leaf);
            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, selector);
            params.addCertStore(java.security.cert.CertStore.getInstance(
                    "Collection",
                    new java.security.cert.CollectionCertStoreParameters(certs)));
            params.setRevocationEnabled(false);
            CertPathBuilder.getInstance("PKIX").build(params);
            return null;
        } catch (Exception ex) {
            return "certificate chain is not trusted by system roots: " + ex.getMessage();
        }
    }

    private static Set<TrustAnchor> systemTrustAnchors() throws Exception {
        Set<TrustAnchor> anchors = new HashSet<>();
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
                    if (cert instanceof X509Certificate) {
                        anchors.add(new TrustAnchor((X509Certificate) cert, null));
                    }
                }
            }
            if (!anchors.isEmpty()) {
                return anchors;
            }
        }
        return anchors;
    }

    private static List<String> collectNames(X509Certificate cert) throws Exception {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Collection<List<?>> sans = cert.getSubjectAlternativeNames();
        if (sans != null) {
            for (List<?> san : sans) {
                if (san == null || san.size() < 2) {
                    continue;
                }
                Object value = san.get(1);
                if (value != null) {
                    names.add(String.valueOf(value));
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
        String expected = serverName.trim().toLowerCase(Locale.ROOT);
        try {
            for (String name : collectNames(cert)) {
                if (name == null) {
                    continue;
                }
                String candidate = name.trim().toLowerCase(Locale.ROOT);
                if (candidate.equals(expected)) {
                    return true;
                }
                if (candidate.startsWith("*.") && expected.endsWith(candidate.substring(1))
                        && expected.indexOf('.') == expected.length() - candidate.length() + 1) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
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
                && (hasText(settings.getHttpsCertificateChain()) || hasText(settings.getHttpsCertificatePath()));
    }

    private static boolean hasPrivateKeyMaterial(AppSettings settings) {
        return settings != null
                && (hasText(settings.getHttpsPrivateKey()) || hasText(settings.getHttpsPrivateKeyPath()));
    }

    private static boolean hasInlinePrivateKey(AppSettings settings) {
        return settings != null && hasText(settings.getHttpsPrivateKey());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class TlsMaterial {
        private final byte[] certificateChain;
        private final byte[] privateKey;

        private TlsMaterial(byte[] certificateChain, byte[] privateKey) {
            this.certificateChain = certificateChain != null ? certificateChain : new byte[0];
            this.privateKey = privateKey != null ? privateKey : new byte[0];
        }
    }
}
