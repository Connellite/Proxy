package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
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

import java.util.List;
import io.github.connellite.proxy.client.rpc.dto.RelayAllowRowDto;
import io.github.connellite.proxy.client.rpc.dto.RelayPageDto;
import io.github.connellite.proxy.client.rpc.dto.RelayQueryRowDto;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteRowDto;
import io.github.connellite.proxy.client.util.AutoRefresh;
import io.github.connellite.proxy.client.util.BindHostList;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

/** DNS answers and the TCP relay that follows SNI. */
public class RelayPage extends Composite {

    private final AppShell shell;
    private final CheckBox dnsEnabled = Forms.checkbox("Enable DNS");
    private final CheckBox tcpEnabled = Forms.checkbox("Enable TCP relay");
    private final CheckBox tcpUseUpstream = Forms.checkbox("Use the selected upstream proxy");
    private final CheckBox dohEnabled = Forms.checkbox("Enable DoH");
    private final CheckBox doqEnabled = Forms.checkbox("Enable DoQ");
    private final ListBox dnsBind = BindHostList.create();
    private final ListBox tcpBind = BindHostList.create();
    private final ListBox dohBind = BindHostList.create();
    private final ListBox doqBind = BindHostList.create();
    private final PlainIntegerBox dnsPort = new PlainIntegerBox();
    private final PlainIntegerBox tcpPort = new PlainIntegerBox();
    private final PlainIntegerBox dohPort = new PlainIntegerBox();
    private final PlainIntegerBox doqPort = new PlainIntegerBox();
    private final TextBox advertised = new TextBox();
    private final TextBox resolver = new TextBox();
    private final TextBox dnsAllowInput = new TextBox();
    private final TextBox tcpAllowInput = new TextBox();
    private final FlowPanel dnsAllowHost = new FlowPanel();
    private final FlowPanel tcpAllowHost = new FlowPanel();
    private final TextBox routeName = new TextBox();
    private final ListBox routeMode = new ListBox();
    private final TextBox upstreamHost = new TextBox();
    private final TextBox upstreamPort = new TextBox();
    private final TextBox answerAddress = new TextBox();
    private final FlowPanel upstreamFields = Forms.twoCol(
            Forms.field("Upstream host", upstreamHost, "Host only, not host:port."),
            Forms.field("Upstream port", upstreamPort, "Empty uses the port the client opened."));
    private final FlowPanel answerField = Forms.field("Answer IP", answerAddress, "One IPv4 or IPv6.");
    private final CheckBox routeEnabled = Forms.checkbox("Enabled");
    private final FlowPanel tableHost = new FlowPanel();
    private final FlowPanel logTable = new FlowPanel();

    public RelayPage(AppShell shell) {
        this.shell = shell;
        dnsPort.setMin(1);
        dnsPort.setMax(65535);
        tcpPort.setMin(1);
        tcpPort.setMax(65535);
        dohPort.setMin(1);
        dohPort.setMax(65535);
        doqPort.setMin(1);
        doqPort.setMax(65535);
        upstreamPort.getElement().setAttribute("inputmode", "numeric");
        routeMode.addItem("Relay", "RELAY");
        routeMode.addItem("Address", "ADDRESS");
        routeMode.addChangeHandler(new ChangeHandler() {
            @Override
            public void onChange(ChangeEvent event) {
                applyRouteMode();
            }
        });
        routeEnabled.setValue(true);
        applyRouteMode();

        FlowPanel root = new FlowPanel();
        root.add(new HTML("<h1>DNS Relay</h1>"));
        Label hint = new Label("DNS on the device points here. A relay name returns this server's address and the "
                + "TCP relay connects by SNI. An address name returns that IP and is not accepted by the relay. "
                + "TLS is not decrypted. Saving replaces the working snapshot; open connections keep the old target.");
        hint.setStyleName("hint");
        root.add(hint);

        root.add(new HTML("<h2>Listeners</h2>"));

        FlowPanel addressPanel = new FlowPanel();
        addressPanel.setStyleName("panel form");
        addressPanel.add(new HTML("<h2>This server</h2>"));
        addressPanel.add(Forms.field("IP for relay names", advertised,
                "DNS answers a relay name with this IP, so the device connects here instead of the real server. "
                        + "One IPv4 or IPv6. A relay route cannot be saved until this is set."));
        addressPanel.add(Forms.field("Resolver for everything else", resolver,
                "host:port of a normal DNS server. Names that are not in the route table are forwarded there. "
                        + "A relay upstream host is resolved there too, so it does not point back at this server."));
        root.add(addressPanel);

        FlowPanel dnsPanel = new FlowPanel();
        dnsPanel.setStyleName("panel form");
        dnsPanel.add(new HTML("<h2>DNS</h2>"));
        dnsPanel.add(dnsEnabled);
        dnsPanel.add(Forms.twoCol(
                Forms.field("DNS bind address", dnsBind,
                        "0.0.0.0 = all interfaces; 127.0.0.1 = this PC only."),
                Forms.field("DNS port", dnsPort)));
        Label dnsHint = new Label("Anyone who can reach this port can query other names through it.");
        dnsHint.setStyleName("muted");
        dnsPanel.add(dnsHint);
        dnsPanel.add(allowEditor(dnsAllowHost, dnsAllowInput, true));
        root.add(dnsPanel);

        FlowPanel dohPanel = new FlowPanel();
        dohPanel.setStyleName("panel form");
        dohPanel.add(new HTML("<h2>DNS over HTTPS</h2>"));
        dohPanel.add(dohEnabled);
        dohPanel.add(Forms.twoCol(
                Forms.field("DoH bind address", dohBind,
                        "0.0.0.0 = all interfaces; 127.0.0.1 = this PC only."),
                Forms.field("DoH port", dohPort)));
        Label dohHint = new Label("GET and POST /dns-query. Uses the certificate from Encryption. "
                + "Cannot share an address and port with the TCP relay.");
        dohHint.setStyleName("muted");
        dohPanel.add(dohHint);
        root.add(dohPanel);

        FlowPanel doqPanel = new FlowPanel();
        doqPanel.setStyleName("panel form");
        doqPanel.add(new HTML("<h2>DNS over QUIC</h2>"));
        doqPanel.add(doqEnabled);
        doqPanel.add(Forms.twoCol(
                Forms.field("DoQ bind address", doqBind,
                        "0.0.0.0 = all interfaces; 127.0.0.1 = this PC only."),
                Forms.field("DoQ port", doqPort)));
        Label doqHint = new Label("QUIC on UDP. Uses the same certificate. "
                + "If the native QUIC library is missing, only this listener stays down.");
        doqHint.setStyleName("muted");
        doqPanel.add(doqHint);
        root.add(doqPanel);

        FlowPanel tcpPanel = new FlowPanel();
        tcpPanel.setStyleName("panel form");
        tcpPanel.add(new HTML("<h2>TCP relay</h2>"));
        tcpPanel.add(tcpEnabled);
        tcpPanel.add(tcpUseUpstream);
        tcpPanel.add(Forms.twoCol(
                Forms.field("TCP bind address", tcpBind,
                        "0.0.0.0 = all interfaces; 127.0.0.1 = this PC only."),
                Forms.field("TCP port", tcpPort)));
        Label tcpHint = new Label("Reads the name from ClientHello and copies the bytes unchanged. "
                + "The upstream must be the server for that name.");
        tcpHint.setStyleName("muted");
        tcpPanel.add(tcpHint);
        tcpPanel.add(allowEditor(tcpAllowHost, tcpAllowInput, false));
        root.add(tcpPanel);

        Button saveSettings = new Button("Save listeners");
        saveSettings.setStyleName("primary");
        saveSettings.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveSettings();
            }
        });
        FlowPanel saveRow = Forms.formActions(saveSettings);
        saveRow.addStyleName("settings-save");
        root.add(saveRow);

        FlowPanel routesPanel = new FlowPanel();
        routesPanel.setStyleName("panel form");
        routesPanel.add(new HTML("<h2>Routes</h2>"));
        routesPanel.add(Forms.twoCol(
                Forms.field("Name", routeName, "Hostname or *.example.com. One row is one name."),
                Forms.field("Mode", routeMode)));
        routesPanel.add(upstreamFields);
        routesPanel.add(answerField);
        routesPanel.add(routeEnabled);
        Button add = new Button("Save route");
        add.setStyleName("primary");
        add.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveRoute();
            }
        });
        routesPanel.add(Forms.formActions(add));
        tableHost.setVisible(false);
        routesPanel.add(tableHost);
        root.add(routesPanel);

        FlowPanel logPanel = new FlowPanel();
        logPanel.setStyleName("panel form");
        FlowPanel logHeader = new FlowPanel();
        logHeader.setStyleName("row-between");
        logHeader.add(new HTML("<h2>Recent queries</h2>"));
        AutoRefresh refresh = new AutoRefresh(new AutoRefresh.Callback() {
            @Override
            public void onRefresh() {
                loadLog();
            }
        });
        shell.attachRefresh(refresh);
        logHeader.add(refresh);
        logPanel.add(logHeader);
        logTable.setStyleName("table-wrap");
        logPanel.add(logTable);
        root.add(logPanel);

        initWidget(root);
        load();
        loadLog();
    }

    private void applyRouteMode() {
        boolean relay = routeMode.getSelectedIndex() < 0
                || "RELAY".equals(routeMode.getValue(routeMode.getSelectedIndex()));
        upstreamFields.setVisible(relay);
        answerField.setVisible(!relay);
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
                tcpEnabled.setValue(result.isTcpEnabled());
                tcpUseUpstream.setValue(result.isTcpUseUpstream());
                BindHostList.fill(dnsBind, result.getBindHostOptions(), result.getDnsBindHost());
                BindHostList.fill(tcpBind, result.getBindHostOptions(), result.getTcpBindHost());
                BindHostList.fill(dohBind, result.getBindHostOptions(), result.getDohBindHost());
                BindHostList.fill(doqBind, result.getBindHostOptions(), result.getDoqBindHost());
                dnsPort.setIntValue(result.getDnsPort());
                tcpPort.setIntValue(result.getTcpPort());
                dohPort.setIntValue(result.getDohPort());
                doqPort.setIntValue(result.getDoqPort());
                dohEnabled.setValue(result.isDohEnabled());
                doqEnabled.setValue(result.isDoqEnabled());
                advertised.setText(result.getAdvertisedAddress() == null ? "" : result.getAdvertisedAddress());
                resolver.setText(result.getUpstreamResolver());
                renderAllows(dnsAllowHost, dnsAllowInput, result.getDnsAllows(), true);
                renderAllows(tcpAllowHost, tcpAllowInput, result.getTcpAllows(), false);
                render(result);
            }
        });
    }

    private FlowPanel allowEditor(FlowPanel host, TextBox input, boolean dns) {
        FlowPanel box = new FlowPanel();
        Label title = new Label("Allowed networks");
        title.getElement().getStyle().setProperty("fontWeight", "600");
        box.add(title);
        Label hint = new Label("Empty allows every address. One IP or CIDR, for example 203.0.113.0/24.");
        hint.setStyleName("field-hint");
        box.add(hint);
        input.setWidth("100%");
        box.add(input);
        Button add = new Button("Add");
        add.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveAllow(input, dns);
            }
        });
        box.add(Forms.formActions(add));
        host.setVisible(false);
        box.add(host);
        return box;
    }

    private void renderAllows(FlowPanel host, TextBox input, List<RelayAllowRowDto> rows, boolean dns) {
        host.clear();
        if (rows == null || rows.isEmpty()) {
            host.setStyleName("");
            host.setVisible(false);
            return;
        }
        host.setStyleName("table-wrap");
        host.setVisible(true);
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        table.setText(0, 0, "Network");
        table.setText(0, 1, "");
        styleHeader(table, 0);
        styleHeader(table, 1);
        int row = 1;
        for (RelayAllowRowDto allow : rows) {
            table.setText(row, 0, text(allow.getCidr()));
            Button remove = new Button("Delete");
            remove.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    deleteAllow(allow.getId(), dns);
                }
            });
            table.setWidget(row, 1, remove);
            row++;
        }
        host.add(table);
    }

    private void saveAllow(TextBox input, boolean dns) {
        AsyncCallback<Void> callback = new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                input.setText("");
                shell.showFlash("Network saved", true);
                load();
            }
        };
        if (dns) {
            shell.getRpc().saveRelayDnsAllow(input.getText(), callback);
        } else {
            shell.getRpc().saveRelayTcpAllow(input.getText(), callback);
        }
    }

    private void deleteAllow(Long id, boolean dns) {
        if (id == null) {
            return;
        }
        AsyncCallback<Void> callback = new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash("Network deleted", true);
                load();
            }
        };
        if (dns) {
            shell.getRpc().deleteRelayDnsAllow(id, callback);
        } else {
            shell.getRpc().deleteRelayTcpAllow(id, callback);
        }
    }

    private void render(RelayPageDto page) {
        tableHost.clear();
        if (page.getRoutes() == null || page.getRoutes().isEmpty()) {
            tableHost.setStyleName("");
            tableHost.setVisible(false);
            return;
        }
        tableHost.setStyleName("table-wrap");
        tableHost.setVisible(true);
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        String[] headers = {"Name", "Mode", "Upstream", "Answer", "Enabled", ""};
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("background", "#efe8dc");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("fontSize", "0.75rem");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("textTransform", "uppercase");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("fontWeight", "700");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("color", "#6b645a");
        }
        int row = 1;
        for (RelayRouteRowDto route : page.getRoutes()) {
                table.setText(row, 0, route.getName());
                table.setText(row, 1, "ADDRESS".equals(route.getMode()) ? "Address" : "Relay");
                String upstream = route.getUpstreamHost() == null ? "" : route.getUpstreamHost();
                if (route.getUpstreamPort() != null && !route.getUpstreamPort().isEmpty()) {
                    upstream = upstream + ":" + route.getUpstreamPort();
                }
                table.setText(row, 2, upstream);
                table.setText(row, 3, route.getAnswerAddress() == null ? "" : route.getAnswerAddress());
                table.setText(row, 4, route.isEnabled() ? "yes" : "no");
                FlowPanel actions = new FlowPanel();
                actions.setStyleName("actions");
                Button remove = new Button("Delete");
                remove.addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        delete(route.getId());
                    }
                });
                actions.add(remove);
                table.setWidget(row, 5, actions);
                row++;
        }
        tableHost.add(table);
    }

    private void loadLog() {
        shell.getRpc().getRelayQueryLog(new AsyncCallback<List<RelayQueryRowDto>>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(List<RelayQueryRowDto> result) {
                renderLog(result);
            }
        });
    }

    private void renderLog(List<RelayQueryRowDto> rows) {
        logTable.clear();
        if (rows == null || rows.isEmpty()) {
            Label empty = new Label("No queries yet.");
            empty.setStyleName("muted");
            logTable.add(empty);
            return;
        }
        FlexTable table = new FlexTable();
        table.setStyleName("users-table query-log");
        String[] headers = {"Time", "Client", "Name", "Type", "Result", "Answer", "Elapsed"};
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
            styleHeader(table, i);
        }
        int row = 1;
        for (RelayQueryRowDto query : rows) {
            table.setText(row, 0, text(query.getTime()));
            table.setText(row, 1, text(query.getClient()));
            table.setText(row, 2, text(query.getName()));
            table.setText(row, 3, text(query.getType()));
            table.setText(row, 4, text(query.getOutcome()));
            table.setText(row, 5, text(query.getAnswer()));
            table.setText(row, 6, text(query.getElapsed()));
            row++;
        }
        logTable.add(table);
    }

    private static void styleHeader(FlexTable table, int column) {
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("background", "#efe8dc");
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("fontSize", "0.75rem");
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("textTransform", "uppercase");
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("fontWeight", "700");
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("color", "#6b645a");
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private void saveSettings() {
        RelayPageDto form = new RelayPageDto();
        form.setDnsEnabled(dnsEnabled.getValue());
        form.setDnsBindHost(BindHostList.selected(dnsBind));
        form.setDnsPort(portOrZero(dnsPort));
        form.setTcpEnabled(tcpEnabled.getValue());
        form.setTcpUseUpstream(tcpUseUpstream.getValue());
        form.setTcpBindHost(BindHostList.selected(tcpBind));
        form.setTcpPort(portOrZero(tcpPort));
        form.setDohEnabled(dohEnabled.getValue());
        form.setDohBindHost(BindHostList.selected(dohBind));
        form.setDohPort(portOrZero(dohPort));
        form.setDoqEnabled(doqEnabled.getValue());
        form.setDoqBindHost(BindHostList.selected(doqBind));
        form.setDoqPort(portOrZero(doqPort));
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

    private void saveRoute() {
        RelayRouteRowDto row = new RelayRouteRowDto();
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
                upstreamHost.setText("");
                upstreamPort.setText("");
                answerAddress.setText("");
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

    private static int portOrZero(PlainIntegerBox box) {
        Integer value = box.getIntValue();
        return value == null ? 0 : value;
    }
}
