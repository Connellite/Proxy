package io.github.connellite.proxy.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.client.SafeHtmlTemplates.Template;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.CommonMessages;
import io.github.connellite.proxy.client.messages.DashboardMessages;
import io.github.connellite.proxy.client.rpc.dto.DashboardDnsGWTDTO;
import io.github.connellite.proxy.client.util.CellCopy;
import io.github.connellite.proxy.client.util.Rpc;

/**
 * DNS relay listener cards for the Dashboard DNS tab.
 */
public class DashboardDnsPage extends Composite {

    private final AppShell shell;
    private final FlowPanel stats = new FlowPanel();
    private final FlowPanel help = new FlowPanel();
    private static final Templates TEMPLATES = GWT.create(Templates.class);

    interface Templates extends SafeHtmlTemplates {
        @Template("<p><strong>{0}</strong></p>")
        SafeHtml heading(String title);

        @Template("<p>{0} → <code>{1}</code></p>")
        SafeHtml endpoint(String label, String endpoint);

        @Template("<p class=\"muted\">{0}</p>")
        SafeHtml empty(String text);
    }

    public DashboardDnsPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        stats.setStyleName("grid stats");

        FlowPanel helpPanel = new FlowPanel();
        helpPanel.setStyleName("panel");
        helpPanel.add(new HTML(UiTemplates.HEADINGS.h2(AppMessages.DASHBOARD.sectionDnsRelay())));
        helpPanel.add(help);
        CellCopy.installCode(help);

        root.add(stats);
        root.add(helpPanel);
        initWidget(root);
    }

    public void refresh() {
        shell.getRpc().getDashboardDns(new AsyncCallback<DashboardDnsGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(DashboardDnsGWTDTO dto) {
                render(dto);
            }
        });
    }

    private void render(DashboardDnsGWTDTO dto) {
        DashboardMessages text = AppMessages.DASHBOARD;
        CommonMessages common = AppMessages.COMMON;
        String stopped = common.stopped();
        String up = common.up();
        String down = common.down();
        stats.clear();
        DashboardStats.addStat(stats, text.statDns(), dto.isDnsRunning() ? dto.getDnsBind() : stopped,
                dto.isDnsRunning() ? up : down);
        DashboardStats.addStat(stats, text.statTcpRelay(), dto.isTcpRunning() ? dto.getTcpBind() : stopped,
                dto.isTcpRunning() ? up : down);
        DashboardStats.addStat(stats, text.statDoh(), dto.isDohRunning() ? dto.getDohBind() : stopped,
                dto.isDohRunning() ? up : down);
        DashboardStats.addStat(stats, text.statDoh3(), dto.isDoh3Running() ? dto.getDoh3Bind() : stopped,
                dto.isDoh3Running() ? up : down);
        DashboardStats.addStat(stats, text.statDot(), dto.isDotRunning() ? dto.getDotBind() : stopped,
                dto.isDotRunning() ? up : down);
        DashboardStats.addStat(stats, text.statDoq(), dto.isDoqRunning() ? dto.getDoqBind() : stopped,
                dto.isDoqRunning() ? up : down);
        DashboardStats.addStat(stats, text.statRoutes(),
                common.ratio(String.valueOf(dto.getEnabledRouteCount()), String.valueOf(dto.getRouteCount())), null);
        DashboardStats.addStat(stats, text.statAdvertised(),
                blank(dto.getAdvertisedAddress()), null);
        DashboardStats.addStat(stats, text.statUpstreamResolver(),
                blank(dto.getUpstreamResolver()), null);
        DashboardStats.addStat(stats, text.statTcpViaUpstream(),
                dto.isTcpUseUpstream() ? common.yes() : common.no(), null);

        help.clear();
        help.add(new HTML(TEMPLATES.heading(text.connectionStrings())));
        boolean any = false;
        any |= appendEndpoint(text.plainDns(), dto.isDnsRunning(), dto.getDnsEndpoint());
        any |= appendEndpoint(text.statDoh(), dto.isDohRunning(), dto.getDohEndpoint());
        any |= appendEndpoint(text.statDoh3(), dto.isDoh3Running(), dto.getDoh3Endpoint());
        any |= appendEndpoint(text.statDot(), dto.isDotRunning(), dto.getDotEndpoint());
        any |= appendEndpoint(text.statDoq(), dto.isDoqRunning(), dto.getDoqEndpoint());
        if (!any) {
            help.add(new HTML(TEMPLATES.empty(text.emptyDnsListeners())));
        }
    }

    private boolean appendEndpoint(String label, boolean running, String endpoint) {
        if (!running || endpoint == null || endpoint.isEmpty()) {
            return false;
        }
        help.add(new HTML(TEMPLATES.endpoint(label, endpoint)));
        return true;
    }

    private static String blank(String value) {
        return value == null || value.isEmpty() ? AppMessages.COMMON.dash() : value;
    }
}
