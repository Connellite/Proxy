package io.github.connellite.proxy.client.ui;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import io.github.connellite.proxy.client.rpc.dto.DashboardDnsDto;
import io.github.connellite.proxy.client.util.CellCopy;
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
        CellCopy.installCode(help);

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

        StringBuilder html = new StringBuilder();
        html.append("<p><strong>Connection strings</strong></p>");
        boolean any = false;
        any |= appendEndpoint(html, "Plain DNS", dto.isDnsRunning(), dto.getDnsEndpoint());
        any |= appendEndpoint(html, "DoH", dto.isDohRunning(), dto.getDohEndpoint());
        any |= appendEndpoint(html, "DoH3", dto.isDoh3Running(), dto.getDoh3Endpoint());
        any |= appendEndpoint(html, "DoT", dto.isDotRunning(), dto.getDotEndpoint());
        any |= appendEndpoint(html, "DoQ", dto.isDoqRunning(), dto.getDoqEndpoint());
        if (!any) {
            html.append("<p class=\"muted\">No running DNS listeners with a public host yet. "
                    + "Set the TLS server name or advertised address, then enable a listener.</p>");
        }
        help.setHTML(html.toString());
    }

    private static boolean appendEndpoint(StringBuilder html, String label, boolean running, String endpoint) {
        if (!running || endpoint == null || endpoint.isEmpty()) {
            return false;
        }
        html.append("<p>").append(SafeHtmlUtils.htmlEscape(label)).append(" → <code>")
                .append(SafeHtmlUtils.htmlEscape(endpoint)).append("</code></p>");
        return true;
    }

    private static String blank(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }
}
