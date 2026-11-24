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
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

import java.util.List;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.RelayMessages;
import io.github.connellite.proxy.client.rpc.dto.RelayAllowRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayQueryRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteModeGWT;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayTcpSessionRowGWTDTO;
import io.github.connellite.proxy.client.util.AutoRefresh;
import io.github.connellite.proxy.client.util.BindHostList;
import io.github.connellite.proxy.client.util.CellCopy;
import io.github.connellite.proxy.client.util.EnumListBox;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

/** DNS answers and the TCP relay that follows SNI. */
public class RelayPage extends Composite {

    private final AppShell shell;
    private final RelayMessages text = AppMessages.RELAY;
    private final CheckBox dnsEnabled = Forms.checkbox(text.enableDns());
    private final CheckBox tcpEnabled = Forms.checkbox(text.enableTcp());
    private final CheckBox tcpUseUpstream = Forms.checkbox(text.tcpUseUpstream());
    private final CheckBox dohEnabled = Forms.checkbox(text.enableDoh());
    private final CheckBox doh3Enabled = Forms.checkbox(text.enableDoh3());
    private final CheckBox dotEnabled = Forms.checkbox(text.enableDot());
    private final CheckBox doqEnabled = Forms.checkbox(text.enableDoq());
    private final BindHostList dnsBind = new BindHostList();
    private final BindHostList tcpBind = new BindHostList();
    private final BindHostList dohBind = new BindHostList();
    private final BindHostList doh3Bind = new BindHostList();
    private final BindHostList dotBind = new BindHostList();
    private final BindHostList doqBind = new BindHostList();
    private final PlainIntegerBox dnsPort = new PlainIntegerBox();
    private final PlainIntegerBox tcpPort = new PlainIntegerBox();
    private final PlainIntegerBox dohPort = new PlainIntegerBox();
    private final PlainIntegerBox doh3Port = new PlainIntegerBox();
    private final PlainIntegerBox dotPort = new PlainIntegerBox();
    private final PlainIntegerBox doqPort = new PlainIntegerBox();
    private final TextBox advertised = new TextBox();
    private final TextBox resolver = new TextBox();
    private final TextBox dnsAllowInput = new TextBox();
    private final TextBox tcpAllowInput = new TextBox();
    private final FlowPanel dnsAllowHost = new FlowPanel();
    private final FlowPanel tcpAllowHost = new FlowPanel();
    private final TextBox routeName = new TextBox();
    private final EnumListBox<RelayRouteModeGWT> routeMode = new EnumListBox<>(RelayRouteModeGWT.values(),
            new EnumListBox.Labels<RelayRouteModeGWT>() {
                @Override
                public String label(RelayRouteModeGWT value) {
                    return AppMessages.routeMode(value);
                }
            });
    private final TextBox upstreamHost = new TextBox();
    private final TextBox upstreamPort = new TextBox();
    private final TextBox answerAddress = new TextBox();
    private final FlowPanel upstreamFields = Forms.twoCol(
            Forms.field(text.upstreamHost(), upstreamHost, text.upstreamHostHint()),
            Forms.field(text.upstreamPort(), upstreamPort, text.upstreamPortHint()));
    private final FlowPanel answerField = Forms.field(text.answerIp(), answerAddress, text.answerIpHint());
    private final CheckBox routeEnabled = Forms.checkbox(AppMessages.COMMON.enabled());
    private final FlowPanel tableHost = new FlowPanel();
    private Long editingRouteId;
    private final FlowPanel logTable = new FlowPanel();
    private final FlowPanel tcpLogTable = new FlowPanel();

    public RelayPage(AppShell shell) {
        this.shell = shell;
        dnsPort.setMin(1);
        dnsPort.setMax(65535);
        tcpPort.setMin(1);
        tcpPort.setMax(65535);
        dohPort.setMin(1);
        dohPort.setMax(65535);
        doh3Port.setMin(1);
        doh3Port.setMax(65535);
        dotPort.setMin(1);
        dotPort.setMax(65535);
        doqPort.setMin(1);
        doqPort.setMax(65535);
        upstreamPort.getElement().setAttribute("inputmode", "numeric");
        routeMode.select(RelayRouteModeGWT.RELAY);
        routeMode.addChangeHandler(new ChangeHandler() {
            @Override
            public void onChange(ChangeEvent event) {
                applyRouteMode();
            }
        });
        routeEnabled.setValue(true);
        applyRouteMode();

        FlowPanel root = new FlowPanel();
        root.add(new HTML(UiTemplates.HEADINGS.h1(text.title())));
        Label hint = new Label(text.hint());
        hint.setStyleName("hint");
        root.add(hint);

        root.add(new HTML(UiTemplates.HEADINGS.h2(text.listeners())));

        FlowPanel addressPanel = new FlowPanel();
        addressPanel.setStyleName("panel form");
        addressPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.thisServer())));
        addressPanel.add(Forms.field(text.advertised(), advertised, text.advertisedHint()));
        addressPanel.add(Forms.field(text.resolver(), resolver, text.resolverHint()));
        root.add(addressPanel);

        FlowPanel dnsPanel = new FlowPanel();
        dnsPanel.setStyleName("panel form");
        dnsPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.dns())));
        dnsPanel.add(dnsEnabled);
        dnsPanel.add(Forms.twoCol(
                Forms.field(text.dnsBind(), dnsBind, AppMessages.COMMON.bindHint()),
                Forms.field(text.dnsPort(), dnsPort)));
        Label dnsHint = new Label(text.dnsHint());
        dnsHint.setStyleName("muted");
        dnsPanel.add(dnsHint);
        dnsPanel.add(allowEditor(dnsAllowHost, dnsAllowInput, true));
        root.add(dnsPanel);

        FlowPanel dohPanel = new FlowPanel();
        dohPanel.setStyleName("panel form");
        dohPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.doh())));
        dohPanel.add(dohEnabled);
        dohPanel.add(Forms.twoCol(
                Forms.field(text.dohBind(), dohBind, AppMessages.COMMON.bindHint()),
                Forms.field(text.dohPort(), dohPort)));
        Label dohHint = new Label(text.dohHint());
        dohHint.setStyleName("muted");
        dohPanel.add(dohHint);
        root.add(dohPanel);

        FlowPanel doh3Panel = new FlowPanel();
        doh3Panel.setStyleName("panel form");
        doh3Panel.add(new HTML(UiTemplates.HEADINGS.h2(text.doh3())));
        doh3Panel.add(doh3Enabled);
        doh3Panel.add(Forms.twoCol(
                Forms.field(text.doh3Bind(), doh3Bind, AppMessages.COMMON.bindHint()),
                Forms.field(text.doh3Port(), doh3Port)));
        Label doh3Hint = new Label(text.doh3Hint());
        doh3Hint.setStyleName("muted");
        doh3Panel.add(doh3Hint);
        root.add(doh3Panel);

        FlowPanel dotPanel = new FlowPanel();
        dotPanel.setStyleName("panel form");
        dotPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.dot())));
        dotPanel.add(dotEnabled);
        dotPanel.add(Forms.twoCol(
                Forms.field(text.dotBind(), dotBind, AppMessages.COMMON.bindHint()),
                Forms.field(text.dotPort(), dotPort)));
        Label dotHint = new Label(text.dotHint());
        dotHint.setStyleName("muted");
        dotPanel.add(dotHint);
        root.add(dotPanel);

        FlowPanel doqPanel = new FlowPanel();
        doqPanel.setStyleName("panel form");
        doqPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.doq())));
        doqPanel.add(doqEnabled);
        doqPanel.add(Forms.twoCol(
                Forms.field(text.doqBind(), doqBind, AppMessages.COMMON.bindHint()),
                Forms.field(text.doqPort(), doqPort)));
        Label doqHint = new Label(text.doqHint());
        doqHint.setStyleName("muted");
        doqPanel.add(doqHint);
        root.add(doqPanel);

        FlowPanel tcpPanel = new FlowPanel();
        tcpPanel.setStyleName("panel form");
        tcpPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.tcp())));
        tcpPanel.add(tcpEnabled);
        tcpPanel.add(tcpUseUpstream);
        tcpPanel.add(Forms.twoCol(
                Forms.field(text.tcpBind(), tcpBind, AppMessages.COMMON.bindHint()),
                Forms.field(text.tcpPort(), tcpPort)));
        Label tcpHint = new Label(text.tcpHint());
        tcpHint.setStyleName("muted");
        tcpPanel.add(tcpHint);
        tcpPanel.add(allowEditor(tcpAllowHost, tcpAllowInput, false));
        root.add(tcpPanel);

        Button saveSettings = new Button(text.saveListeners());
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
        routesPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.routes())));
        routesPanel.add(Forms.twoCol(
                Forms.field(text.routeName(), routeName, text.routeNameHint()),
                Forms.field(text.mode(), routeMode)));
        routesPanel.add(upstreamFields);
        routesPanel.add(answerField);
        routesPanel.add(routeEnabled);
        Button add = new Button(text.saveRoute());
        add.setStyleName("primary");
        add.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveRoute();
            }
        });
        routesPanel.add(Forms.formActions(add));
        Label routeHint = new Label(text.routeHint());
        routeHint.setStyleName("muted");
        routesPanel.add(routeHint);
        tableHost.setVisible(false);
        routesPanel.add(tableHost);
        root.add(routesPanel);

        FlowPanel logPanel = new FlowPanel();
        logPanel.setStyleName("panel form");
        FlowPanel logHeader = new FlowPanel();
        logHeader.setStyleName("row-between");
        logHeader.add(new HTML(UiTemplates.HEADINGS.h2(text.recentQueries())));
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

        FlowPanel tcpLogPanel = new FlowPanel();
        tcpLogPanel.setStyleName("panel form");
        FlowPanel tcpLogHeader = new FlowPanel();
        tcpLogHeader.setStyleName("row-between");
        tcpLogHeader.add(new HTML(UiTemplates.HEADINGS.h2(text.recentTcp())));
        AutoRefresh tcpRefresh = new AutoRefresh(new AutoRefresh.Callback() {
            @Override
            public void onRefresh() {
                loadTcpLog();
            }
        });
        shell.attachRefresh(tcpRefresh);
        tcpLogHeader.add(tcpRefresh);
        tcpLogPanel.add(tcpLogHeader);
        tcpLogTable.setStyleName("table-wrap");
        tcpLogPanel.add(tcpLogTable);
        root.add(tcpLogPanel);

        initWidget(root);
        load();
        loadLog();
        loadTcpLog();
    }

    private void applyRouteMode() {
        RelayRouteModeGWT mode = routeMode.selected(RelayRouteModeGWT.RELAY);
        boolean relay = mode == RelayRouteModeGWT.RELAY;
        upstreamFields.setVisible(relay);
        answerField.setVisible(!relay);
    }

    private void load() {
        shell.getRpc().getRelayPage(new AsyncCallback<RelayPageGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(RelayPageGWTDTO result) {
                dnsEnabled.setValue(result.isDnsEnabled());
                tcpEnabled.setValue(result.isTcpEnabled());
                tcpUseUpstream.setValue(result.isTcpUseUpstream());
                dnsBind.fill(result.getBindHostOptions(), result.getDnsBindHost());
                tcpBind.fill(result.getBindHostOptions(), result.getTcpBindHost());
                dohBind.fill(result.getBindHostOptions(), result.getDohBindHost());
                doh3Bind.fill(result.getBindHostOptions(), result.getDoh3BindHost());
                dotBind.fill(result.getBindHostOptions(), result.getDotBindHost());
                doqBind.fill(result.getBindHostOptions(), result.getDoqBindHost());
                dnsPort.setIntValue(result.getDnsPort());
                tcpPort.setIntValue(result.getTcpPort());
                dohPort.setIntValue(result.getDohPort());
                doh3Port.setIntValue(result.getDoh3Port());
                dotPort.setIntValue(result.getDotPort());
                doqPort.setIntValue(result.getDoqPort());
                dohEnabled.setValue(result.isDohEnabled());
                doh3Enabled.setValue(result.isDoh3Enabled());
                dotEnabled.setValue(result.isDotEnabled());
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
        Label title = new Label(text.allowedNetworks());
        title.getElement().getStyle().setProperty("fontWeight", "600");
        box.add(title);
        Label hint = new Label(text.allowedNetworksHint());
        hint.setStyleName("field-hint");
        box.add(hint);
        input.setWidth("100%");
        box.add(input);
        Button add = new Button(AppMessages.COMMON.add());
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

    private void renderAllows(FlowPanel host, TextBox input, List<RelayAllowRowGWTDTO> rows, boolean dns) {
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
        table.setText(0, 0, text.network());
        table.setText(0, 1, "");
        styleHeader(table, 0);
        styleHeader(table, 1);
        int row = 1;
        for (RelayAllowRowGWTDTO allow : rows) {
            table.setText(row, 0, text(allow.getCidr()));
            Button remove = new Button(AppMessages.COMMON.delete());
            remove.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    deleteAllow(allow.getId(), dns);
                }
            });
            table.setWidget(row, 1, remove);
            row++;
        }
        CellCopy.install(table);
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
                shell.showFlash(text.networkSaved(), true);
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
                shell.showFlash(text.networkDeleted(), true);
                load();
            }
        };
        if (dns) {
            shell.getRpc().deleteRelayDnsAllow(id, callback);
        } else {
            shell.getRpc().deleteRelayTcpAllow(id, callback);
        }
    }

    private void render(RelayPageGWTDTO page) {
        tableHost.clear();
        if (page.getRoutes() == null || page.getRoutes().isEmpty()) {
            tableHost.setStyleName("");
            tableHost.setVisible(false);
            return;
        }
        tableHost.setStyleName("table-wrap");
        tableHost.setVisible(true);
        FlexTable table = new FlexTable();
        table.setStyleName("users-table route-table");
        String[] headers = {text.colName(), text.colMode(), text.colUpstream(), text.colAnswer(), text.colEnabled(), ""};
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("background", "#efe8dc");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("fontSize", "0.75rem");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("textTransform", "uppercase");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("fontWeight", "700");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("color", "#6b645a");
        }
        int row = 1;
        final List<RelayRouteRowGWTDTO> routes = page.getRoutes();
        for (RelayRouteRowGWTDTO route : routes) {
                table.setText(row, 0, route.getName());
                RelayRouteModeGWT mode = route.getMode() == null ? RelayRouteModeGWT.RELAY : route.getMode();
                table.setText(row, 1, AppMessages.routeMode(mode));
                String upstream = route.getUpstreamHost() == null || route.getUpstreamHost().isEmpty()
                        ? text.realAddress() : route.getUpstreamHost();
                if (mode == RelayRouteModeGWT.ADDRESS) {
                    upstream = "";
                } else if (route.getUpstreamPort() != null && !route.getUpstreamPort().isEmpty()) {
                    upstream = upstream + ":" + route.getUpstreamPort();
                }
                table.setText(row, 2, upstream);
                table.setText(row, 3, route.getAnswerAddress() == null ? "" : route.getAnswerAddress());
                table.setText(row, 4, route.isEnabled() ? AppMessages.COMMON.yes() : AppMessages.COMMON.no());
                FlowPanel actions = new FlowPanel();
                actions.setStyleName("actions");
                Button remove = new Button(AppMessages.COMMON.delete());
                remove.addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        delete(route.getId());
                    }
                });
                actions.add(remove);
                table.setWidget(row, 5, actions);
                table.getRowFormatter().addStyleName(row, "route-row");
                if (editingRouteId != null && editingRouteId.equals(route.getId())) {
                    table.getRowFormatter().addStyleName(row, "route-selected");
                }
                row++;
        }
        table.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                HTMLTable.Cell cell = table.getCellForEvent(event);
                if (cell == null || cell.getRowIndex() < 1 || cell.getCellIndex() == 5) {
                    return;
                }
                int index = cell.getRowIndex() - 1;
                if (index >= routes.size()) {
                    return;
                }
                fillRoute(routes.get(index));
                for (int i = 1; i < table.getRowCount(); i++) {
                    table.getRowFormatter().removeStyleName(i, "route-selected");
                }
                table.getRowFormatter().addStyleName(cell.getRowIndex(), "route-selected");
            }
        });
        CellCopy.install(table);
        tableHost.add(table);
    }

    private void loadLog() {
        shell.getRpc().getRelayQueryLog(new AsyncCallback<List<RelayQueryRowGWTDTO>>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(List<RelayQueryRowGWTDTO> result) {
                renderLog(result);
            }
        });
    }

    private void renderLog(List<RelayQueryRowGWTDTO> rows) {
        logTable.clear();
        if (rows == null || rows.isEmpty()) {
            Label empty = new Label(text.noQueries());
            empty.setStyleName("muted");
            logTable.add(empty);
            return;
        }
        FlexTable table = new FlexTable();
        table.setStyleName("users-table query-log");
        String[] headers = {
                text.colTime(), text.colClient(), text.colProto(), text.colName(),
                text.colType(), text.colResult(), text.colAnswer(), text.colElapsed()
        };
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
            styleHeader(table, i);
        }
        int row = 1;
        for (RelayQueryRowGWTDTO query : rows) {
            table.setText(row, 0, text(query.getTime()));
            table.setText(row, 1, text(query.getClient()));
            table.setText(row, 2, text(query.getProtocol()));
            table.setText(row, 3, text(query.getName()));
            table.setText(row, 4, text(query.getType()));
            table.setText(row, 5, text(query.getOutcome()));
            table.setText(row, 6, text(query.getAnswer()));
            table.setText(row, 7, text(query.getElapsed()));
            row++;
        }
        CellCopy.install(table);
        logTable.add(table);
    }

    private void loadTcpLog() {
        shell.getRpc().getRelayTcpSessionLog(new AsyncCallback<List<RelayTcpSessionRowGWTDTO>>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(List<RelayTcpSessionRowGWTDTO> result) {
                renderTcpLog(result);
            }
        });
    }

    private void renderTcpLog(List<RelayTcpSessionRowGWTDTO> rows) {
        tcpLogTable.clear();
        if (rows == null || rows.isEmpty()) {
            Label empty = new Label(text.noTcpSessions());
            empty.setStyleName("muted");
            tcpLogTable.add(empty);
            return;
        }
        FlexTable table = new FlexTable();
        table.setStyleName("users-table query-log");
        String[] headers = {text.colTime(), text.colClient(), text.colHost(), text.colResult(), text.colElapsed()};
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
            styleHeader(table, i);
        }
        int row = 1;
        for (RelayTcpSessionRowGWTDTO session : rows) {
            table.setText(row, 0, text(session.getTime()));
            table.setText(row, 1, text(session.getClient()));
            table.setText(row, 2, text(session.getHostname()));
            table.setText(row, 3, text(session.getOutcome()));
            table.setText(row, 4, text(session.getElapsed()));
            row++;
        }
        CellCopy.install(table);
        tcpLogTable.add(table);
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
        RelayPageGWTDTO form = new RelayPageGWTDTO();
        form.setDnsEnabled(dnsEnabled.getValue());
        form.setDnsBindHost(dnsBind.selected());
        form.setDnsPort(portOrZero(dnsPort));
        form.setTcpEnabled(tcpEnabled.getValue());
        form.setTcpUseUpstream(tcpUseUpstream.getValue());
        form.setTcpBindHost(tcpBind.selected());
        form.setTcpPort(portOrZero(tcpPort));
        form.setDohEnabled(dohEnabled.getValue());
        form.setDohBindHost(dohBind.selected());
        form.setDohPort(portOrZero(dohPort));
        form.setDoh3Enabled(doh3Enabled.getValue());
        form.setDoh3BindHost(doh3Bind.selected());
        form.setDoh3Port(portOrZero(doh3Port));
        form.setDotEnabled(dotEnabled.getValue());
        form.setDotBindHost(dotBind.selected());
        form.setDotPort(portOrZero(dotPort));
        form.setDoqEnabled(doqEnabled.getValue());
        form.setDoqBindHost(doqBind.selected());
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
                shell.showFlash(text.listenersSaved(), true);
                load();
            }
        });
    }

    private void fillRoute(RelayRouteRowGWTDTO route) {
        editingRouteId = route.getId();
        routeName.setText(text(route.getName()));
        selectMode(route.getMode());
        upstreamHost.setText(text(route.getUpstreamHost()));
        upstreamPort.setText(text(route.getUpstreamPort()));
        answerAddress.setText(text(route.getAnswerAddress()));
        routeEnabled.setValue(route.isEnabled());
        applyRouteMode();
    }

    private void selectMode(RelayRouteModeGWT mode) {
        routeMode.select(mode == null ? RelayRouteModeGWT.RELAY : mode);
    }

    private void clearRouteForm() {
        editingRouteId = null;
        routeName.setText("");
        upstreamHost.setText("");
        upstreamPort.setText("");
        answerAddress.setText("");
        routeEnabled.setValue(true);
        selectMode(RelayRouteModeGWT.RELAY);
        applyRouteMode();
    }

    private void saveRoute() {
        RelayRouteRowGWTDTO row = new RelayRouteRowGWTDTO();
        row.setId(editingRouteId);
        row.setName(routeName.getText());
        row.setMode(routeMode.selected(RelayRouteModeGWT.RELAY));
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
                shell.showFlash(text.routeSaved(), true);
                clearRouteForm();
                load();
            }
        });
    }

    private void delete(Long id) {
        if (id == null) {
            return;
        }
        final Long removed = id;
        shell.getRpc().deleteRelayRoute(id, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash(text.routeDeleted(), true);
                if (removed.equals(editingRouteId)) {
                    clearRouteForm();
                }
                load();
            }
        });
    }

    private static int portOrZero(PlainIntegerBox box) {
        Integer value = box.getIntValue();
        return value == null ? 0 : value;
    }
}
