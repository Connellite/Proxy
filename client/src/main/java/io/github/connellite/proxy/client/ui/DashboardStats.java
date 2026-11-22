package io.github.connellite.proxy.client.ui;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

/** Shared dashboard stat cards. */
final class DashboardStats {

    private DashboardStats() {
    }

    static void addStat(FlowPanel host, String label, String value, String valueStyle) {
        FlowPanel card = new FlowPanel();
        card.setStyleName("stat");
        Label lbl = new Label(label);
        lbl.setStyleName("label");
        Label val = new Label(value == null ? "" : value);
        val.setStyleName(valueStyle == null ? "value" : "value " + valueStyle);
        card.add(lbl);
        card.add(val);
        host.add(card);
    }
}
