package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Where the first TLS handshake record to an upstream is cut, for admin UI and GWT RPC.
 */
public enum ClientHelloSplitModeGWT implements IsSerializable, HasGwtLabel {

    FIXED("Fixed position"),
    BEFORE_SNI("Before SNI"),
    AFTER_SNI("After SNI"),
    MIDDLE_SNI("Middle of SNI"),
    RANDOM_SNI("Random inside SNI");

    private final String label;

    ClientHelloSplitModeGWT(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static ClientHelloSplitModeGWT fromWire(String value) {
        if (value == null || value.isEmpty()) {
            return FIXED;
        }
        try {
            return ClientHelloSplitModeGWT.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FIXED;
        }
    }
}
