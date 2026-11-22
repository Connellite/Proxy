package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Relay route mode for admin UI and GWT RPC.
 */
public enum RelayRouteModeGWT implements IsSerializable, HasGwtLabel {

    RELAY("Relay"),
    ADDRESS("Address");

    private final String label;

    RelayRouteModeGWT(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static RelayRouteModeGWT fromWire(String value) {
        if (value == null || value.isEmpty()) {
            return RELAY;
        }
        try {
            return RelayRouteModeGWT.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return RELAY;
        }
    }
}
