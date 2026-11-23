package io.github.connellite.proxy.dns.upstream;

import io.github.connellite.proxy.dns.relay.HostPort;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Parsed upstream DNS resolver from the single settings string.
 */
public record UpstreamDnsEndpoint(Kind kind, String host, int port, String uri) {

    public enum Kind {
        PLAIN,
        DOH,
        DOT,
        DOH3,
        DOQ
    }

    public static final UpstreamDnsEndpoint DEFAULT = plain("1.1.1.1", 53);

    public UpstreamDnsEndpoint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("Host is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if ((kind == Kind.DOH || kind == Kind.DOH3) && (uri == null || uri.isBlank())) {
            throw new IllegalArgumentException("URI is required for " + kind);
        }
    }

    public static UpstreamDnsEndpoint plain(String host, int port) {
        return new UpstreamDnsEndpoint(Kind.PLAIN, host, port, null);
    }

    public static UpstreamDnsEndpoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("sdns://")) {
            throw new IllegalArgumentException("DNSCrypt (sdns://) is not supported");
        }
        if (lower.startsWith("https://")) {
            return parseHttp(Kind.DOH, trimmed, 443);
        }
        if (lower.startsWith("h3://")) {
            return parseHttp(Kind.DOH3, "https://" + trimmed.substring(5), 443);
        }
        if (lower.startsWith("tls://")) {
            HostPort hostPort = HostPort.parse(trimmed.substring(6), 853);
            return new UpstreamDnsEndpoint(Kind.DOT, hostPort.host(), hostPort.port(), null);
        }
        if (lower.startsWith("quic://")) {
            HostPort hostPort = HostPort.parse(trimmed.substring(7), 853);
            return new UpstreamDnsEndpoint(Kind.DOQ, hostPort.host(), hostPort.port(), null);
        }
        int scheme = lower.indexOf("://");
        if (scheme >= 0) {
            throw new IllegalArgumentException("Unsupported resolver scheme: " + trimmed.substring(0, scheme));
        }
        HostPort hostPort = HostPort.parse(trimmed, 53);
        return plain(hostPort.host(), hostPort.port());
    }

    private static UpstreamDnsEndpoint parseHttp(Kind kind, String httpsUri, int defaultPort) {
        URI uri;
        try {
            uri = URI.create(httpsUri);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid resolver URL", ex);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Host is required");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
        String path = uri.getRawPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = "/dns-query";
        }
        StringBuilder normalized = new StringBuilder("https://");
        boolean v6 = uri.getHost().indexOf(':') >= 0;
        if (v6) {
            normalized.append('[').append(uri.getHost()).append(']');
        } else {
            normalized.append(uri.getHost());
        }
        if (uri.getPort() > 0) {
            normalized.append(':').append(uri.getPort());
        }
        normalized.append(path);
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            normalized.append('?').append(uri.getRawQuery());
        }
        return new UpstreamDnsEndpoint(kind, uri.getHost(), port, normalized.toString());
    }

    @Override
    public String toString() {
        return switch (kind) {
            case PLAIN -> host + ':' + port;
            case DOH, DOH3 -> uri;
            case DOT -> "tls://" + host + ':' + port;
            case DOQ -> "quic://" + host + ':' + port;
        };
    }
}
