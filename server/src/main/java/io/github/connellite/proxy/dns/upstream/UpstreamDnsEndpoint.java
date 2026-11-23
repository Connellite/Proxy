package io.github.connellite.proxy.dns.upstream;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/**
 * Parsed upstream DNS resolver from the single settings string (AdGuard-style address).
 */
public record UpstreamDnsEndpoint(Kind kind, String host, int port, String uri, String address) {

    public enum Kind {
        PLAIN,
        TCP,
        DOH,
        DOT,
        DOH3,
        DOQ
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L149
     */
    private static final int DEFAULT_PORT_PLAIN = 53;

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L152
     */
    private static final int DEFAULT_PORT_DOH = 443;

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L155
     */
    private static final int DEFAULT_PORT_DOT = 853;

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L161
     */
    private static final int DEFAULT_PORT_DOQ = 853;

    public static final UpstreamDnsEndpoint DEFAULT = plain("1.1.1.1", DEFAULT_PORT_PLAIN);

    public UpstreamDnsEndpoint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(address, "address");
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
        String normalized = preserveHost(host);
        return new UpstreamDnsEndpoint(Kind.PLAIN, normalized, port, null, formatHostPort(normalized, port));
    }

    /**
     * Copy-paste listener address for the dashboard. Empty when {@code host} is blank.
     * Default ports are omitted ({@code 53}/{@code 443}/{@code 853}); DoH/DoH3 append {@code /dns-query}.
     */
    public static String formatListener(Kind kind, String host, int port) {
        if (host == null || host.isBlank()) {
            return "";
        }
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case PLAIN, TCP -> authority(host, port, DEFAULT_PORT_PLAIN);
            case DOH -> url("https", host, port, DEFAULT_PORT_DOH, "/dns-query");
            case DOH3 -> url("h3", host, port, DEFAULT_PORT_DOH, "/dns-query");
            case DOT -> url("tls", host, port, DEFAULT_PORT_DOT, null);
            case DOQ -> url("quic", host, port, DEFAULT_PORT_DOQ, null);
        };
    }

    private static String url(String scheme, String host, int port, int defaultPort, String path) {
        String auth = authority(host, port, defaultPort);
        if (path == null || path.isEmpty()) {
            return scheme + "://" + auth;
        }
        return scheme + "://" + auth + path;
    }

    private static String authority(String host, int port, int defaultPort) {
        String bracketed = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        if (port == defaultPort) {
            return bracketed;
        }
        return bracketed + ":" + port;
    }

    /**
     * Parses an AdGuard-style upstream address into an endpoint (no client is created).
     *
     * <p>https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L188-L218
     */
    public static UpstreamDnsEndpoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String addr = raw.trim();
        try {
            URI uu = parseAddressToUrl(addr);
            validateUpstreamUrl(uu);
            return urlToUpstream(uu);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException | URISyntaxException ex) {
            throw new IllegalArgumentException("failed to parse " + addr + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L188-L218
     */
    private static URI parseAddressToUrl(String addr) throws URISyntaxException {
        if (addr.contains("://")) {
            return new URI(addr);
        }
        // Bare address → udp:// like AddressToUpstream (Host holds the raw addr).
        // https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L209-L213
        if (InetAddresses.isInetAddress(addr)) {
            return new URI("udp", null, preserveHost(addr), -1, null, null, null);
        }
        // Guava HostAndPort treats "host:" as host-only; AdGuard rejects empty port via SplitHostPort.
        if (addr.endsWith(":")) {
            throw new IllegalArgumentException("invalid port :");
        }
        HostAndPort hostPort;
        try {
            hostPort = HostAndPort.fromString(addr).requireBracketsForIPv6();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
        String host = hostPort.getHost();
        if (hostPort.hasPort()) {
            int port = hostPort.getPort();
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("invalid port " + port);
            }
            return new URI("udp", null, host, port, null, null, null);
        }
        return new URI("udp", null, host, -1, null, null, null);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L220-L260
     */
    private static void validateUpstreamUrl(URI u) {
        String scheme = scheme(u);
        if ("sdns".equals(scheme)) {
            return;
        }
        // Mirror net.SplitHostPort on url.Host: trailing ':' means empty port.
        String authority = u.getRawAuthority() == null ? "" : u.getRawAuthority();
        if (authority.endsWith(":")) {
            throw new IllegalArgumentException("invalid port :");
        }
        String host = stripBrackets(u.getHost());
        if (host == null || host.isBlank()) {
            String possibleIp = authority;
            int bracketEnd = authority.indexOf(']');
            if (authority.startsWith("[") && bracketEnd > 0) {
                possibleIp = authority.substring(1, bracketEnd);
                if (bracketEnd + 1 < authority.length() && authority.charAt(bracketEnd + 1) == ':') {
                    parsePort(authority.substring(bracketEnd + 2));
                }
            } else {
                int colon = authority.lastIndexOf(':');
                if (colon >= 0 && authority.indexOf(':') == colon) {
                    possibleIp = authority.substring(0, colon);
                    parsePort(authority.substring(colon + 1));
                }
            }
            if (InetAddresses.isInetAddress(possibleIp)) {
                return;
            }
            validateDomainName(possibleIp);
            return;
        }
        if (u.getPort() > 65535) {
            throw new IllegalArgumentException("invalid port " + u.getPort());
        }
        if (InetAddresses.isInetAddress(host)) {
            return;
        }
        validateDomainName(host);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L262-L277
     */
    private static UpstreamDnsEndpoint urlToUpstream(URI uu) {
        String scheme = scheme(uu);
        return switch (scheme) {
            case "sdns" -> throw new IllegalArgumentException("DNSCrypt (sdns://) is not supported");
            case "udp" -> fromPlain(uu, Kind.PLAIN);
            case "tcp" -> fromPlain(uu, Kind.TCP);
            case "quic" -> fromDoq(uu);
            case "tls" -> fromDot(uu);
            case "h3", "https" -> fromDoh(uu);
            default -> throw new IllegalArgumentException("unsupported url scheme: " + scheme);
        };
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/plain.go#L52-L70
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/plain.go#L75-L84
     */
    private static UpstreamDnsEndpoint fromPlain(URI addr, Kind kind) {
        URI withPort = addPort(addr, DEFAULT_PORT_PLAIN);
        String host = requireHost(withPort);
        int port = withPort.getPort();
        String address = kind == Kind.TCP
                ? "tcp://" + formatHostPort(host, port)
                : formatHostPort(host, port);
        return new UpstreamDnsEndpoint(kind, host, port, null, address);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/dot.go#L58-L60
     */
    private static UpstreamDnsEndpoint fromDot(URI addr) {
        URI withPort = addPort(addr, DEFAULT_PORT_DOT);
        String host = requireHost(withPort);
        int port = withPort.getPort();
        return new UpstreamDnsEndpoint(Kind.DOT, host, port, null, "tls://" + formatHostPort(host, port));
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/doq.go#L119-L121
     */
    private static UpstreamDnsEndpoint fromDoq(URI addr) {
        URI withPort = addPort(addr, DEFAULT_PORT_DOQ);
        String host = requireHost(withPort);
        int port = withPort.getPort();
        return new UpstreamDnsEndpoint(Kind.DOQ, host, port, null, "quic://" + formatHostPort(host, port));
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/doh.go#L92-L102
     */
    private static UpstreamDnsEndpoint fromDoh(URI addr) {
        Kind kind = "h3".equals(scheme(addr)) ? Kind.DOH3 : Kind.DOH;
        URI normalized = addPort(addr, DEFAULT_PORT_DOH);
        if (kind == Kind.DOH3) {
            normalized = withScheme(normalized, "https");
        }
        String host = requireHost(normalized);
        // Rebuild so host form matches AdGuard (brackets / compression).
        URI display = withHost(normalized, host, normalized.getPort(), normalized.getRawPath(), normalized.getRawQuery());
        String address = display.toString();
        return new UpstreamDnsEndpoint(kind, host, normalized.getPort(), address, address);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L319-L330
     */
    private static URI addPort(URI u, int port) {
        if (u.getPort() > 0) {
            return u;
        }
        return withHost(u, requireHost(u), port, u.getRawPath(), u.getRawQuery());
    }

    private static URI withScheme(URI u, String scheme) {
        try {
            return new URI(scheme, u.getUserInfo(), u.getHost(), u.getPort(), u.getPath(), u.getQuery(), u.getFragment());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid resolver URL", ex);
        }
    }

    private static URI withHost(URI base, String host, int port, String path, String query) {
        try {
            return new URI(base.getScheme(), base.getUserInfo(), host, port, path, query, base.getFragment());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid resolver URL", ex);
        }
    }

    private static String requireHost(URI u) {
        String host = stripBrackets(u.getHost());
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("invalid address : domain name is empty");
        }
        return preserveHost(host);
    }

    /**
     * Keep IPv6 form as written (incl. IPv4-mapped {@code ::ffff:x.x.x.x}); Guava's
     * {@code toAddrString} collapses those to IPv4, unlike AdGuard {@code Address()}.
     */
    private static String preserveHost(String host) {
        host = stripBrackets(host);
        if (InetAddresses.isInetAddress(host)) {
            if (host.indexOf(':') >= 0) {
                return host;
            }
            return InetAddresses.toAddrString(InetAddresses.forString(host));
        }
        return host;
    }

    private static String stripBrackets(String host) {
        if (host != null && host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static String scheme(URI u) {
        return u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
    }

    private static int parsePort(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("invalid port :");
        }
        try {
            int port = Integer.parseInt(raw);
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("invalid port " + raw);
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid port " + raw, ex);
        }
    }

    /**
     * Lightweight stand-in for AdGuard {@code netutil.ValidateDomainName}.
     *
     * <p>https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L254-L258
     */
    private static void validateDomainName(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("invalid address : domain name is empty");
        }
        String[] labels = host.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty()) {
                throw new IllegalArgumentException("invalid address " + host);
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                boolean ok = c >= 'a' && c <= 'z'
                        || c >= 'A' && c <= 'Z'
                        || c >= '0' && c <= '9'
                        || c == '-'
                        || c == '_';
                if (!ok) {
                    throw new IllegalArgumentException("invalid address " + host);
                }
            }
        }
        String tld = labels[labels.length - 1];
        boolean allNumeric = true;
        for (int i = 0; i < tld.length(); i++) {
            if (!Character.isDigit(tld.charAt(i))) {
                allNumeric = false;
                break;
            }
        }
        if (allNumeric) {
            throw new IllegalArgumentException("invalid address " + host + ": all octets are numeric");
        }
    }

    private static String formatHostPort(String host, int port) {
        if (host.indexOf(':') >= 0) {
            return "[" + host + "]:" + port;
        }
        return host + ':' + port;
    }

    @Override
    public String toString() {
        return address;
    }
}
