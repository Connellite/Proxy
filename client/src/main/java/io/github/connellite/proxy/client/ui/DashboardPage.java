package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import io.github.connellite.proxy.client.util.AutoRefresh;

/** Dashboard shell with Proxy and DNS tabs. */
public class DashboardPage extends Composite {

    private final DashboardProxyPage proxyPage;
    private final DashboardDnsPage dnsPage;
    private final Anchor proxyTab = new Anchor("Proxy");
    private final Anchor dnsTab = new Anchor("DNS");

    public DashboardPage(AppShell shell) {
        proxyPage = new DashboardProxyPage(shell);
        dnsPage = new DashboardDnsPage(shell);

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

        FlowPanel tabs = new FlowPanel();
        tabs.setStyleName("dash-tabs");
        proxyTab.setHref("#");
        dnsTab.setHref("#");
        proxyTab.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                showProxy();
            }
        });
        dnsTab.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                showDns();
            }
        });
        tabs.add(proxyTab);
        tabs.add(dnsTab);

        root.add(header);
        root.add(tabs);
        root.add(proxyPage);
        root.add(dnsPage);
        initWidget(root);

        showProxy();
        load();
    }

    private void load() {
        proxyPage.refresh();
        dnsPage.refresh();
    }

    private void showProxy() {
        proxyPage.setVisible(true);
        dnsPage.setVisible(false);
        proxyTab.setStyleName("dash-tab active");
        dnsTab.setStyleName("dash-tab");
    }

    private void showDns() {
        proxyPage.setVisible(false);
        dnsPage.setVisible(true);
        proxyTab.setStyleName("dash-tab");
        dnsTab.setStyleName("dash-tab active");
    }
}
