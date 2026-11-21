package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.rpc.dto.RelayPageDto;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteRowDto;
import io.github.connellite.proxy.client.util.Rpc;

/** DNS answers and the TCP relay that follows SNI. */
public class RelayPage extends Composite {

    private final AppShell shell;
    private final CheckBox dnsEnabled = new CheckBox("DNS enabled");
    private final TextBox dnsBind = new TextBox();
    private final TextBox dnsPort = new TextBox();
    private final CheckBox tcpEnabled = new CheckBox("TCP relay enabled");
    private final TextBox tcpBind = new TextBox();
    private final TextBox tcpPort = new TextBox();
    private final TextBox advertised = new TextBox();
    private final TextBox resolver = new TextBox();
    private final TextBox routeName = new TextBox();
    private final ListBox routeMode = new ListBox();
    private final TextBox upstreamHost = new TextBox();
    private final TextBox upstreamPort = new TextBox();
    private final TextBox answerAddress = new TextBox();
    private final CheckBox routeEnabled = new CheckBox("Enabled");
    private final FlowPanel tableHost = new FlowPanel();

    public RelayPage(AppShell shell) {
        this.shell = shell;
        routeMode.addItem("Relay", "RELAY");
        routeMode.addItem("Address", "ADDRESS");
        routeEnabled.setValue(true);

        FlowPanel root = new FlowPanel();
        root.add(new HTML("<h1>Relay</h1>"));
        Label hint = new Label("DNS on the device points here. A relay name returns this server's address and the "
                + "TCP relay connects by SNI. An address name returns that IP and is not accepted by the relay. "
                + "TLS is not decrypted. Saving replaces the working snapshot; open connections keep the old target.");
        hint.setStyleName("hint");
        root.add(hint);

        root.add(field("Address clients receive", advertised));
        root.add(dnsEnabled);
        root.add(field("DNS bind", dnsBind));
        root.add(field("DNS port", dnsPort));
        root.add(tcpEnabled);
        root.add(field("TCP bind", tcpBind));
        root.add(field("TCP port", tcpPort));
        root.add(field("Upstream resolver", resolver));
        Button saveSettings = new Button("Save listeners");
        saveSettings.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveSettings();
            }
        });
        root.add(saveSettings);

        root.add(new HTML("<h2>Routes</h2>"));
        root.add(field("Name", routeName));
        root.add(routeMode);
        root.add(field("Upstream host", upstreamHost));
        root.add(field("Upstream port", upstreamPort));
        root.add(field("Answer IP", answerAddress));
        root.add(routeEnabled);
        Button add = new Button("Save route");
        add.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveRoute(null);
            }
        });
        root.add(add);
        tableHost.setStyleName("table-wrap");
        root.add(tableHost);
        initWidget(root);
        load();
    }

    private static FlowPanel field(String label, TextBox box) {
        FlowPanel panel = new FlowPanel();
        panel.add(new Label(label));
        box.setWidth("100%");
        panel.add(box);
        return panel;
    }

    private void load() {
        shell.getRpc().getRelayPage(new AsyncCallback<RelayPageDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(RelayPageDto result) {
                dnsEnabled.setValue(result.isDnsEnabled());
                dnsBind.setText(result.getDnsBindHost());
                dnsPort.setText(Integer.toString(result.getDnsPort()));
                tcpEnabled.setValue(result.isTcpEnabled());
                tcpBind.setText(result.getTcpBindHost());
                tcpPort.setText(Integer.toString(result.getTcpPort()));
                advertised.setText(result.getAdvertisedAddress() == null ? "" : result.getAdvertisedAddress());
                resolver.setText(result.getUpstreamResolver());
                render(result);
            }
        });
    }

    private void render(RelayPageDto page) {
        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        String[] headers = {"Name", "Mode", "Upstream", "Answer", "Enabled", ""};
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
        }
        int row = 1;
        if (page.getRoutes() != null) {
            for (RelayRouteRowDto route : page.getRoutes()) {
                table.setText(row, 0, route.getName());
                table.setText(row, 1, route.getMode());
                String upstream = route.getUpstreamHost() == null ? "" : route.getUpstreamHost();
                if (route.getUpstreamPort() != null && !route.getUpstreamPort().isEmpty()) {
                    upstream = upstream + ":" + route.getUpstreamPort();
                }
                table.setText(row, 2, upstream);
                table.setText(row, 3, route.getAnswerAddress() == null ? "" : route.getAnswerAddress());
                table.setText(row, 4, route.isEnabled() ? "yes" : "no");
                Button remove = new Button("Delete");
                remove.addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        delete(route.getId());
                    }
                });
                table.setWidget(row, 5, remove);
                row++;
            }
        }
        tableHost.add(table);
    }

    private void saveSettings() {
        RelayPageDto form = new RelayPageDto();
        form.setDnsEnabled(dnsEnabled.getValue());
        form.setDnsBindHost(dnsBind.getText());
        form.setDnsPort(parsePort(dnsPort.getText()));
        form.setTcpEnabled(tcpEnabled.getValue());
        form.setTcpBindHost(tcpBind.getText());
        form.setTcpPort(parsePort(tcpPort.getText()));
        form.setAdvertisedAddress(advertised.getText());
        form.setUpstreamResolver(resolver.getText());
        shell.getRpc().saveRelaySettings(form, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash("Relay listeners saved", true);
                load();
            }
        });
    }

    private void saveRoute(Long id) {
        RelayRouteRowDto row = new RelayRouteRowDto();
        row.setId(id);
        row.setName(routeName.getText());
        row.setMode(routeMode.getValue(routeMode.getSelectedIndex()));
        row.setUpstreamHost(upstreamHost.getText());
        row.setUpstreamPort(upstreamPort.getText());
        row.setAnswerAddress(answerAddress.getText());
        row.setEnabled(routeEnabled.getValue());
        shell.getRpc().saveRelayRoute(row, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash("Route saved", true);
                routeName.setText("");
                load();
            }
        });
    }

    private void delete(Long id) {
        if (id == null) {
            return;
        }
        shell.getRpc().deleteRelayRoute(id, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash("Route deleted", true);
                load();
            }
        });
    }

    private static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
