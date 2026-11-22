package io.github.connellite.proxy.relay;

import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;

/**
 * {@code host}, {@code host:port}, or {@code [ipv6]:port}.
 */
public record HostPort(String host, int port) {

    public static HostPort parse(String raw, int defaultPort) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Host is required");
        }
        HostAndPort parsed = HostAndPort.fromString(raw.trim()).requireBracketsForIPv6();
        if (parsed.getHost().isEmpty()) {
            throw new IllegalArgumentException("Host is required");
        }
        int port = parsed.hasPort() ? parsed.getPort() : defaultPort;
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        return new HostPort(parsed.getHost(), port);
    }

    public static int parsePort(String raw) {
        Integer port = Ints.tryParse(raw == null ? "" : raw.trim());
        if (port == null || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        return port;
    }
}
