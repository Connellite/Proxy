package io.github.connellite.proxy.client.ui;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import io.github.connellite.proxy.client.rpc.dto.DashboardProxyDto;
import io.github.connellite.proxy.client.util.Formatters;
import io.github.connellite.proxy.client.util.Rpc;

/** Proxy listener and traffic cards for the Dashboard Proxy tab. */
public class DashboardProxyPage extends Composite {

    private final AppShell shell;
    private final FlowPanel stats = new FlowPanel();
    private final Label errorLabel = new Label();
    private final HTML zeroOmega = new HTML();

    public DashboardProxyPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        stats.setStyleName("grid stats");
        errorLabel.setStyleName("flash err");
        errorLabel.setVisible(false);

        FlowPanel zeroPanel = new FlowPanel();
        zeroPanel.setStyleName("panel");
        zeroPanel.add(new HTML("<h2>ZeroOmega</h2>"));
        zeroPanel.add(zeroOmega);

        root.add(stats);
        root.add(errorLabel);
        root.add(zeroPanel);
        initWidget(root);
    }

    public void refresh() {
        shell.getRpc().getDashboardProxy(new AsyncCallback<DashboardProxyDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(DashboardProxyDto dto) {
                render(dto);
            }
        });
    }

    private void render(DashboardProxyDto dto) {
        stats.clear();
        DashboardStats.addStat(stats, "HTTP proxy", dto.isHttpRunning() ? dto.getHttpBind() : "stopped",
                dto.isHttpRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "HTTPS proxy (TLS)", dto.isHttpsRunning() ? dto.getHttpsBind() : "stopped",
                dto.isHttpsRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "SOCKS4/5 proxy", dto.isSocksRunning() ? dto.getSocksBind() : "stopped",
                dto.isSocksRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "SSH tunnel proxy", dto.isSshRunning() ? dto.getSshBind() : "stopped",
                dto.isSshRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "Active client TCP channels", String.valueOf(dto.getActiveConnections()), null);
        DashboardStats.addStat(stats, "Users", dto.getEnabledUsers() + " / " + dto.getUserCount(), null);
        DashboardStats.addStat(stats, "Traffic up (since start)", Formatters.formatBytes(dto.getSessionBytesUp()), null);
        DashboardStats.addStat(stats, "Traffic down (since start)", Formatters.formatBytes(dto.getSessionBytesDown()), null);
        DashboardStats.addStat(stats, "Traffic up (all time)", Formatters.formatBytes(dto.getTotalBytesUp()), null);
        DashboardStats.addStat(stats, "Traffic down (all time)", Formatters.formatBytes(dto.getTotalBytesDown()), null);

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
                        + "SOCKS4 only when SOCKS auth is <em>off</em>. Chrome often lacks SOCKS password auth.</p>"
                        + "<p>SSH tunnels → host + port <strong>" + dto.getSshPort()
                        + "</strong> (enable in Settings). Password or a key from the user SSH page. "
                        + "Use PuTTY/OpenSSH local/remote port forward; shell is disabled.</p>");
    }
}
