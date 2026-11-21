package io.github.connellite.proxy.relay;

/** {@code host}, {@code host:port}, or {@code [ipv6]:port}. */
public final class HostPort {

    private final String host;
    private final int port;

    public HostPort(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public static HostPort parse(String raw, int defaultPort) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Host is required");
        }
        String value = raw.trim();
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end <= 1) {
                throw new IllegalArgumentException("Invalid address: " + raw);
            }
            String host = value.substring(1, end);
            int port = defaultPort;
            if (value.length() > end + 1) {
                if (value.charAt(end + 1) != ':') {
                    throw new IllegalArgumentException("Invalid address: " + raw);
                }
                port = parsePort(value.substring(end + 2));
            }
            return new HostPort(host, port);
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            return new HostPort(value.substring(0, colon), parsePort(value.substring(colon + 1)));
        }
        return new HostPort(value, defaultPort);
    }

    public static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }
}
