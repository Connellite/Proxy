package io.github.connellite.proxy.client.ui;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import io.github.connellite.proxy.client.rpc.dto.DashboardDto;
import io.github.connellite.proxy.client.util.AutoRefresh;
import io.github.connellite.proxy.client.util.Formatters;
import io.github.connellite.proxy.client.util.Rpc;

public class DashboardPage extends Composite {

    private final AppShell shell;
    private final FlowPanel stats = new FlowPanel();
    private final Label errorLabel = new Label();
    private final HTML zeroOmega = new HTML();

    public DashboardPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        header.add(new HTML("<h1>Dashboard</h1>"));
        AutoRefresh refresh = new AutoRefresh(new AutoRefresh.Callback() {
            @Override
            public void onRefresh() {
                load();
            }
        });
        shell.attachRefresh(refresh);
        header.add(refresh);

        stats.setStyleName("grid stats");
        errorLabel.setStyleName("flash err");
        errorLabel.setVisible(false);

        FlowPanel activeHelp = new FlowPanel();
        activeHelp.setStyleName("panel");
        activeHelp.add(new HTML("<h2>Active connections</h2>"
                + "<p class=\"muted\">Count of <strong>open inbound TCP sockets</strong> from clients "
                + "(browser/ZeroOmega) that were accepted after auth (or with auth off). "
                + "One keep-alive / CONNECT tunnel = 1. Drops to 0 when the client closes or idle timeout hits. "
                + "See <code>docs/PROTOCOLS.md</code>.</p>"));

        FlowPanel zeroPanel = new FlowPanel();
        zeroPanel.setStyleName("panel");
        zeroPanel.add(new HTML("<h2>ZeroOmega</h2>"));
        zeroPanel.add(zeroOmega);

        root.add(header);
        root.add(stats);
        root.add(errorLabel);
        root.add(activeHelp);
        root.add(zeroPanel);
        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getDashboard(new AsyncCallback<DashboardDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(DashboardDto dto) {
                render(dto);
            }
        });
    }

    private void render(DashboardDto dto) {
        stats.clear();
        addStat("HTTP proxy", dto.isHttpRunning() ? dto.getHttpBind() : "stopped",
                dto.isHttpRunning() ? "up" : "down");
        addStat("HTTPS proxy (TLS)", dto.isHttpsRunning() ? dto.getHttpsBind() : "stopped",
                dto.isHttpsRunning() ? "up" : "down");
        addStat("SOCKS4/5 proxy", dto.isSocksRunning() ? dto.getSocksBind() : "stopped",
                dto.isSocksRunning() ? "up" : "down");
        addStat("Active client TCP channels", String.valueOf(dto.getActiveConnections()), null);
        addStat("Users", dto.getEnabledUsers() + " / " + dto.getUserCount(), null);
        addStat("Traffic up (since start)", Formatters.formatBytes(dto.getSessionBytesUp()), null);
        addStat("Traffic down (since start)", Formatters.formatBytes(dto.getSessionBytesDown()), null);
        addStat("Traffic up (all time)", Formatters.formatBytes(dto.getTotalBytesUp()), null);
        addStat("Traffic down (all time)", Formatters.formatBytes(dto.getTotalBytesDown()), null);

        if (dto.getLastError() != null && !dto.getLastError().isEmpty()) {
            errorLabel.setText(dto.getLastError());
            errorLabel.setVisible(true);
        } else {
            errorLabel.setVisible(false);
        }

        zeroOmega.setHTML(
                "<p>HTTP → protocol <code>HTTP</code>, port <strong>" + dto.getHttpPort() + "</strong>.</p>"
                        + "<p>HTTPS → protocol <code>HTTPS</code>, port <strong>" + dto.getHttpsPort()
                        + "</strong> (enable in Settings; self-signed cert).</p>"
                        + "<p>SOCKS5 / SOCKS4 → same host, port <strong>" + dto.getSocksPort() + "</strong>. "
                        + "SOCKS4 only when SOCKS auth is <em>off</em>. Chrome often lacks SOCKS password auth.</p>");
    }

    private void addStat(String label, String value, String valueStyle) {
        FlowPanel card = new FlowPanel();
        card.setStyleName("stat");
        Label lbl = new Label(label);
        lbl.setStyleName("label");
        Label val = new Label(value == null ? "" : value);
        val.setStyleName(valueStyle == null ? "value" : "value " + valueStyle);
        card.add(lbl);
        card.add(val);
        stats.add(card);
    }
}
