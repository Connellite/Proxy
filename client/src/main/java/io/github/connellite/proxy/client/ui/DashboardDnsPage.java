package io.github.connellite.proxy.client.ui;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import io.github.connellite.proxy.client.rpc.dto.DashboardDnsDto;
import io.github.connellite.proxy.client.util.Rpc;

/**
 * DNS relay listener cards for the Dashboard DNS tab.
 */
public class DashboardDnsPage extends Composite {

    private final AppShell shell;
    private final FlowPanel stats = new FlowPanel();
    private final HTML help = new HTML();

    public DashboardDnsPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        stats.setStyleName("grid stats");

        FlowPanel helpPanel = new FlowPanel();
        helpPanel.setStyleName("panel");
        helpPanel.add(new HTML("<h2>DNS Relay</h2>"));
        helpPanel.add(help);

        root.add(stats);
        root.add(helpPanel);
        initWidget(root);
    }

    public void refresh() {
        shell.getRpc().getDashboardDns(new AsyncCallback<DashboardDnsDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(DashboardDnsDto dto) {
                render(dto);
            }
        });
    }

    private void render(DashboardDnsDto dto) {
        stats.clear();
        DashboardStats.addStat(stats, "DNS", dto.isDnsRunning() ? dto.getDnsBind() : "stopped",
                dto.isDnsRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "TCP relay", dto.isTcpRunning() ? dto.getTcpBind() : "stopped",
                dto.isTcpRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "DoH", dto.isDohRunning() ? dto.getDohBind() : "stopped",
                dto.isDohRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "DoH3", dto.isDoh3Running() ? dto.getDoh3Bind() : "stopped",
                dto.isDoh3Running() ? "up" : "down");
        DashboardStats.addStat(stats, "DoT", dto.isDotRunning() ? dto.getDotBind() : "stopped",
                dto.isDotRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "DoQ", dto.isDoqRunning() ? dto.getDoqBind() : "stopped",
                dto.isDoqRunning() ? "up" : "down");
        DashboardStats.addStat(stats, "Routes", dto.getEnabledRouteCount() + " / " + dto.getRouteCount(), null);
        DashboardStats.addStat(stats, "Advertised address",
                blank(dto.getAdvertisedAddress()), null);
        DashboardStats.addStat(stats, "Upstream resolver",
                blank(dto.getUpstreamResolver()), null);
        DashboardStats.addStat(stats, "TCP via upstream",
                dto.isTcpUseUpstream() ? "yes" : "no", null);

        help.setHTML(
                "<p>Plain DNS → UDP/TCP port <strong>" + dto.getDnsPort() + "</strong>.</p>"
                        + "<p>TCP SNI relay → port <strong>" + dto.getTcpPort()
                        + "</strong> (TLS passthrough by hostname).</p>"
                        + "<p>DoH → HTTPS <code>/dns-query</code> on port <strong>" + dto.getDohPort()
                        + "</strong>.</p>"
                        + "<p>DoH3 → HTTP/3 <code>/dns-query</code> on port <strong>" + dto.getDoh3Port()
                        + "</strong>.</p>"
                        + "<p>DoT → TLS DNS on port <strong>" + dto.getDotPort() + "</strong>.</p>"
                        + "<p>DoQ → QUIC DNS on port <strong>" + dto.getDoqPort() + "</strong>.</p>");
    }

    private static String blank(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }
}
