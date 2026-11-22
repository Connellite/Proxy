package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Auto-refresh interval options for admin UI ListBoxes.
 */
public enum AutoRefreshIntervalGWT implements IsSerializable, HasGwtLabel {

    OFF("Off", 0),
    S1("1s", 1),
    S2("2s", 2),
    S3("3s", 3),
    S5("5s", 5),
    S10("10s", 10),
    S30("30s", 30);

    private final String label;
    private final int seconds;

    AutoRefreshIntervalGWT(String label, int seconds) {
        this.label = label;
        this.seconds = seconds;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public int getSeconds() {
        return seconds;
    }

    public static AutoRefreshIntervalGWT defaultInterval() {
        return S3;
    }
}
