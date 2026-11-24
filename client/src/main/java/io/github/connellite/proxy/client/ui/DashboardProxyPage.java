package io.github.connellite.proxy.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.client.SafeHtmlTemplates.Template;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.CommonMessages;
import io.github.connellite.proxy.client.messages.DashboardMessages;
import io.github.connellite.proxy.client.rpc.dto.DashboardProxyGWTDTO;
import io.github.connellite.proxy.client.util.Formatters;
import io.github.connellite.proxy.client.util.Rpc;

/** Proxy listener and traffic cards for the Dashboard Proxy tab. */
public class DashboardProxyPage extends Composite {

    private final AppShell shell;
    private final FlowPanel stats = new FlowPanel();
    private final Label errorLabel = new Label();
    private final HTML zeroOmega = new HTML();
    private static final Templates TEMPLATES = GWT.create(Templates.class);

    interface Templates extends SafeHtmlTemplates {
        @Template("<p>{0}</p><p>{1}</p><p>{2}</p><p>{3}</p>")
        SafeHtml zeroOmega(SafeHtml http, SafeHtml https, SafeHtml socks, SafeHtml ssh);
    }

    public DashboardProxyPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        stats.setStyleName("grid stats");
        errorLabel.setStyleName("flash err");
        errorLabel.setVisible(false);

        FlowPanel zeroPanel = new FlowPanel();
        zeroPanel.setStyleName("panel");
        zeroPanel.add(new HTML(UiTemplates.HEADINGS.h2(AppMessages.DASHBOARD.sectionZeroOmega())));
        zeroPanel.add(zeroOmega);

        root.add(stats);
        root.add(errorLabel);
        root.add(zeroPanel);
        initWidget(root);
    }

    public void refresh() {
        shell.getRpc().getDashboardProxy(new AsyncCallback<DashboardProxyGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(DashboardProxyGWTDTO dto) {
                render(dto);
            }
        });
    }

    private void render(DashboardProxyGWTDTO dto) {
        DashboardMessages text = AppMessages.DASHBOARD;
        CommonMessages common = AppMessages.COMMON;
        String stopped = common.stopped();
        String up = common.up();
        String down = common.down();
        stats.clear();
        DashboardStats.addStat(stats, text.statHttpProxy(), dto.isHttpRunning() ? dto.getHttpBind() : stopped,
                dto.isHttpRunning() ? up : down);
        DashboardStats.addStat(stats, text.statHttpsProxy(), dto.isHttpsRunning() ? dto.getHttpsBind() : stopped,
                dto.isHttpsRunning() ? up : down);
        DashboardStats.addStat(stats, text.statSocksProxy(), dto.isSocksRunning() ? dto.getSocksBind() : stopped,
                dto.isSocksRunning() ? up : down);
        DashboardStats.addStat(stats, text.statSshProxy(), dto.isSshRunning() ? dto.getSshBind() : stopped,
                dto.isSshRunning() ? up : down);
        DashboardStats.addStat(stats, text.statActiveChannels(), String.valueOf(dto.getActiveConnections()), null);
        DashboardStats.addStat(stats, text.statUsers(),
                common.ratio(String.valueOf(dto.getEnabledUsers()), String.valueOf(dto.getUserCount())), null);
        DashboardStats.addStat(stats, text.statTrafficUpSession(), Formatters.formatBytes(dto.getSessionBytesUp()), null);
        DashboardStats.addStat(stats, text.statTrafficDownSession(), Formatters.formatBytes(dto.getSessionBytesDown()), null);
        DashboardStats.addStat(stats, text.statTrafficUpAll(), Formatters.formatBytes(dto.getTotalBytesUp()), null);
        DashboardStats.addStat(stats, text.statTrafficDownAll(), Formatters.formatBytes(dto.getTotalBytesDown()), null);

        if (dto.getLastError() != null && !dto.getLastError().isEmpty()) {
            errorLabel.setText(dto.getLastError());
            errorLabel.setVisible(true);
        } else {
            errorLabel.setVisible(false);
        }

        zeroOmega.setHTML(TEMPLATES.zeroOmega(
                text.zeroOmegaHttp(dto.getHttpPort()),
                text.zeroOmegaHttps(dto.getHttpsPort()),
                text.zeroOmegaSocks(dto.getSocksPort()),
                text.zeroOmegaSsh(dto.getSshPort())));
    }
}
