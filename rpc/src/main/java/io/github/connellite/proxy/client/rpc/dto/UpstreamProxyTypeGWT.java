package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Upstream proxy type for admin UI and GWT RPC.
 */
public enum UpstreamProxyTypeGWT implements IsSerializable, HasGwtLabel {

    HTTP("HTTP", 8080),
    HTTPS("HTTPS", 443),
    SOCKS5("SOCKS5", 1080),
    SSH("SSH", 22);

    private final String label;
    private final int defaultPort;

    UpstreamProxyTypeGWT(String label, int defaultPort) {
        this.label = label;
        this.defaultPort = defaultPort;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public int defaultPort() {
        return defaultPort;
    }

    public static UpstreamProxyTypeGWT fromWire(String value) {
        if (value == null || value.isEmpty()) {
            return HTTP;
        }
        try {
            return UpstreamProxyTypeGWT.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return HTTP;
        }
    }
}
