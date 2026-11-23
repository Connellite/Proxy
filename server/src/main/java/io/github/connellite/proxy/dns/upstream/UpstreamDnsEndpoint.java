package io.github.connellite.proxy.dns.upstream;

import com.google.common.net.InetAddresses;

import java.net.IDN;
import java.util.Locale;
import java.util.Objects;

/**
 * Parsed upstream DNS resolver from the single settings string (AdGuard-style address).
 *
 * <p>{@code address} mirrors AdGuard {@code Upstream.Address()}; {@code uri} is the request URL for DoH/DoH3.
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

    /**
     * https://github.com/AdguardTeam/golibs/blob/master/netutil/addr.go
     */
    private static final int MAX_DOMAIN_LABEL_LEN = 63;

    /**
     * https://github.com/AdguardTeam/golibs/blob/master/netutil/addr.go
     */
    private static final int MAX_DOMAIN_NAME_LEN = 253;

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
        String bare = stripBrackets(host);
        return new UpstreamDnsEndpoint(Kind.PLAIN, bare, port, null, joinHostPort(bare, port));
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
            case DOH -> "https://" + authority(host, port, DEFAULT_PORT_DOH) + "/dns-query";
            case DOH3 -> "h3://" + authority(host, port, DEFAULT_PORT_DOH) + "/dns-query";
            case DOT -> "tls://" + authority(host, port, DEFAULT_PORT_DOT);
            case DOQ -> "quic://" + authority(host, port, DEFAULT_PORT_DOQ);
        };
    }

    private static String authority(String host, int port, int defaultPort) {
        String bare = stripBrackets(host);
        String bracketed = bare.indexOf(':') >= 0 ? "[" + bare + "]" : bare;
        return port == defaultPort ? bracketed : bracketed + ":" + port;
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
        UpstreamUrl uu = addr.contains("://")
                ? UpstreamUrl.parse(addr)
                : new UpstreamUrl("udp", null, addr, "", null, null);
        validateUpstreamUrl(uu);
        return urlToUpstream(uu);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L220-L260
     */
    private static void validateUpstreamUrl(UpstreamUrl u) {
        if ("sdns".equals(u.scheme())) {
            return;
        }
        String host = u.host();
        HostPort split = splitHostPort(host);
        if (split != null) {
            parsePort(split.port());
            host = split.host();
        }
        String possibleIp = host;
        if (host.length() >= "[::]".length() && host.startsWith("[") && host.endsWith("]")) {
            possibleIp = host.substring(1, host.length() - 1);
        }
        if (InetAddresses.isInetAddress(possibleIp)) {
            return;
        }
        validateDomainName(host);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L262-L277
     */
    private static UpstreamDnsEndpoint urlToUpstream(UpstreamUrl uu) {
        return switch (uu.scheme()) {
            case "sdns" -> throw new IllegalArgumentException("DNSCrypt (sdns://) is not supported");
            case "udp" -> fromPlain(uu, Kind.PLAIN);
            case "tcp" -> fromPlain(uu, Kind.TCP);
            case "quic" -> fromUrl(uu, Kind.DOQ, DEFAULT_PORT_DOQ);
            case "tls" -> fromUrl(uu, Kind.DOT, DEFAULT_PORT_DOT);
            case "h3", "https" -> fromDoh(uu);
            default -> throw new IllegalArgumentException("unsupported url scheme: " + uu.scheme());
        };
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/plain.go#L52-L84
     */
    private static UpstreamDnsEndpoint fromPlain(UpstreamUrl addr, Kind kind) {
        UpstreamUrl withPort = addPort(addr, DEFAULT_PORT_PLAIN);
        HostPort hp = requireHostPort(withPort);
        String address = kind == Kind.TCP ? withPort.toString() : withPort.host();
        return new UpstreamDnsEndpoint(kind, hp.host(), parsePort(hp.port()), null, address);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/dot.go#L58-L60
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/doq.go#L119-L121
     */
    private static UpstreamDnsEndpoint fromUrl(UpstreamUrl addr, Kind kind, int defaultPort) {
        UpstreamUrl withPort = addPort(addr, defaultPort);
        HostPort hp = requireHostPort(withPort);
        return new UpstreamDnsEndpoint(kind, hp.host(), parsePort(hp.port()), null, withPort.toString());
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/doh.go#L92-L102
     */
    private static UpstreamDnsEndpoint fromDoh(UpstreamUrl addr) {
        Kind kind = "h3".equals(addr.scheme()) ? Kind.DOH3 : Kind.DOH;
        UpstreamUrl withPort = addPort(addr, DEFAULT_PORT_DOH).withScheme("https");
        HostPort hp = requireHostPort(withPort);
        String address = withPort.toString();
        return new UpstreamDnsEndpoint(kind, hp.host(), parsePort(hp.port()), withPort.requestUri(), address);
    }

    /**
     * https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L319-L330
     */
    private static UpstreamUrl addPort(UpstreamUrl u, int port) {
        if (splitHostPort(u.host()) != null) {
            return u;
        }
        return u.withHost(joinHostPort(stripBrackets(u.host()), port));
    }

    private static HostPort requireHostPort(UpstreamUrl u) {
        HostPort hp = splitHostPort(u.host());
        if (hp == null || hp.host().isEmpty()) {
            throw new IllegalArgumentException("invalid address : domain name is empty");
        }
        return hp;
    }

    /**
     * Go {@code net.SplitHostPort}: {@code null} when {@code hostport} has no port or is malformed.
     */
    private static HostPort splitHostPort(String hostport) {
        if (hostport.startsWith("[")) {
            int end = hostport.indexOf(']');
            if (end < 0 || end + 1 >= hostport.length() || hostport.charAt(end + 1) != ':') {
                return null;
            }
            String host = hostport.substring(1, end);
            String port = hostport.substring(end + 2);
            if (host.indexOf('[') >= 0 || host.indexOf(']') >= 0 || port.indexOf(']') >= 0) {
                return null;
            }
            return new HostPort(host, port);
        }
        int colon = hostport.lastIndexOf(':');
        if (colon < 0 || hostport.indexOf(':') != colon) {
            return null;
        }
        String host = hostport.substring(0, colon);
        if (host.indexOf('[') >= 0 || host.indexOf(']') >= 0) {
            return null;
        }
        return new HostPort(host, hostport.substring(colon + 1));
    }

    /**
     * Go {@code strconv.ParseUint(port, 10, 16)}: digits only, no sign.
     */
    private static int parsePort(String raw) {
        if (raw.isEmpty() || raw.length() > 5 || !raw.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("invalid port " + raw);
        }
        int port = Integer.parseInt(raw);
        if (port > 65535) {
            throw new IllegalArgumentException("invalid port " + raw);
        }
        return port;
    }

    /**
     * Port of golibs {@code netutil.ValidateDomainName}: non-TLD labels are only length-checked,
     * the TLD must be a hostname label with at least one non-digit.
     *
     * <p>https://github.com/AdguardTeam/golibs/blob/master/netutil/addr.go
     */
    private static void validateDomainName(String host) {
        String name = host;
        if (!name.chars().allMatch(c -> c < 0x80)) {
            try {
                name = IDN.toASCII(name, IDN.ALLOW_UNASSIGNED);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid address " + host + ": " + ex.getMessage(), ex);
            }
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("invalid address " + host + ": domain name is empty");
        }
        if (name.length() > MAX_DOMAIN_NAME_LEN) {
            throw new IllegalArgumentException("invalid address " + host + ": domain name is too long");
        }
        String[] labels = name.split("\\.", -1);
        for (int i = 0; i < labels.length - 1; i++) {
            if (labels[i].isEmpty() || labels[i].length() > MAX_DOMAIN_LABEL_LEN) {
                throw new IllegalArgumentException("invalid address " + host + ": bad domain name label");
            }
        }
        String tld = labels[labels.length - 1];
        if (!isValidHostnameLabel(tld)) {
            throw new IllegalArgumentException("invalid address " + host + ": bad top-level domain name label");
        }
        if (tld.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("invalid address " + host + ": all octets are numeric");
        }
    }

    private static boolean isValidHostnameLabel(String label) {
        int len = label.length();
        if (len == 0 || len > MAX_DOMAIN_LABEL_LEN) {
            return false;
        }
        if (!isHostOuterChar(label.charAt(0)) || !isHostOuterChar(label.charAt(len - 1))) {
            return false;
        }
        for (int i = 1; i < len - 1; i++) {
            char c = label.charAt(i);
            if (c != '-' && !isHostOuterChar(c)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isHostOuterChar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }

    /**
     * Go {@code netutil.JoinHostPort}.
     */
    private static String joinHostPort(String host, int port) {
        return host.indexOf(':') >= 0 ? "[" + host + "]:" + port : host + ":" + port;
    }

    private static String stripBrackets(String host) {
        if (host != null && host.length() > 2 && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    @Override
    public String toString() {
        return address;
    }

    private record HostPort(String host, String port) {
    }

    /**
     * Subset of Go {@code url.URL}: {@code host} keeps brackets and port exactly as written.
     */
    private record UpstreamUrl(String scheme, String userinfo, String host, String path, String query,
                               String fragment) {

        /**
         * Go {@code url.Parse} split order: fragment, query, then authority up to the first {@code /}.
         */
        static UpstreamUrl parse(String raw) {
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c < 0x20 || c == 0x7f) {
                    throw new IllegalArgumentException("failed to parse " + raw + ": invalid control character in URL");
                }
            }
            int schemeEnd = raw.indexOf("://");
            String scheme = raw.substring(0, schemeEnd);
            if (scheme.isEmpty() || !isScheme(scheme)) {
                throw new IllegalArgumentException("failed to parse " + raw + ": missing protocol scheme");
            }
            String rest = raw.substring(schemeEnd + 3);
            String fragment = null;
            int hash = rest.indexOf('#');
            if (hash >= 0) {
                fragment = rest.substring(hash + 1);
                rest = rest.substring(0, hash);
            }
            String query = null;
            int question = rest.indexOf('?');
            if (question >= 0) {
                query = rest.substring(question + 1);
                rest = rest.substring(0, question);
            }
            int slash = rest.indexOf('/');
            String authority = slash >= 0 ? rest.substring(0, slash) : rest;
            String path = slash >= 0 ? rest.substring(slash) : "";
            String userinfo = null;
            int at = authority.lastIndexOf('@');
            if (at >= 0) {
                userinfo = authority.substring(0, at);
                authority = authority.substring(at + 1);
            }
            if (authority.indexOf(' ') >= 0) {
                throw new IllegalArgumentException("failed to parse " + raw + ": invalid character \" \" in host name");
            }
            return new UpstreamUrl(scheme.toLowerCase(Locale.ROOT), userinfo, authority, path, query, fragment);
        }

        private static boolean isScheme(String scheme) {
            if (!Character.isLetter(scheme.charAt(0))) {
                return false;
            }
            return scheme.chars().allMatch(c -> c < 0x80
                    && (Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.'));
        }

        UpstreamUrl withHost(String newHost) {
            return new UpstreamUrl(scheme, userinfo, newHost, path, query, fragment);
        }

        UpstreamUrl withScheme(String newScheme) {
            return new UpstreamUrl(newScheme, userinfo, host, path, query, fragment);
        }

        /**
         * URL for the HTTP client: no userinfo or fragment.
         */
        String requestUri() {
            return scheme + "://" + host + path + (query == null ? "" : "?" + query);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(scheme).append("://");
            if (userinfo != null) {
                sb.append(userinfo).append('@');
            }
            sb.append(host).append(path);
            if (query != null) {
                sb.append('?').append(query);
            }
            if (fragment != null) {
                sb.append('#').append(fragment);
            }
            return sb.toString();
        }
    }
}
