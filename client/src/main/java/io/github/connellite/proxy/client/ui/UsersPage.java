package io.github.connellite.proxy.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.client.SafeHtmlTemplates.Template;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.UsersMessages;
import io.github.connellite.proxy.client.rpc.dto.UserConnectionRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UserRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UsersPageGWTDTO;
import io.github.connellite.proxy.client.util.AutoRefresh;
import io.github.connellite.proxy.client.util.CellCopy;
import io.github.connellite.proxy.client.util.Formatters;
import io.github.connellite.proxy.client.util.Rpc;
import io.github.connellite.proxy.client.util.Tables;

import java.util.List;

public class UsersPage extends Composite {

    private final AppShell shell;
    private final FlowPanel tableHost = new FlowPanel();
    private final FlowPanel connectionTable = new FlowPanel();
    private static final Templates TEMPLATES = GWT.create(Templates.class);

    interface Templates extends SafeHtmlTemplates {
        @Template("<span class=\"badge admin\">{0}</span> <span class=\"badge\">{1}</span>")
        SafeHtml adminAndUser(String admin, String user);

        @Template("<span class=\"badge admin\">{0}</span>")
        SafeHtml adminOnly(String admin);

        @Template("<span class=\"badge\">{0}</span>")
        SafeHtml userOnly(String user);

        @Template("<span class=\"muted-cell\">{0}</span>")
        SafeHtml none(String dash);

        @Template("<span class=\"badge ok\">{0}</span>")
        SafeHtml active(String label);

        @Template("<span class=\"badge muted\">{0}</span>")
        SafeHtml muted(String label);

        @Template("<span class=\"badge warn\">{0}</span>")
        SafeHtml warn(String label);

        @Template("<span>{0}</span><span> / {1}</span>")
        SafeHtml connections(int active, String limit);

        @Template("<div class=\"metric-line\">↑ <span class=\"num\">{0}</span> <span class=\"muted-cell\">/ {1}</span></div><div class=\"metric-line\">↓ <span class=\"num\">{2}</span> <span class=\"muted-cell\">/ {3}</span></div>")
        SafeHtml speed(String up, String upLimit, String down, String downLimit);

        @Template("<div class=\"metric-line traffic\">↑ <span class=\"num\">{0}</span></div><div class=\"metric-line\">↓ <span class=\"num\">{1}</span></div><div class=\"metric-line muted-cell\">Σ {2} / {3}</div>")
        SafeHtml traffic(String up, String down, String used, String limit);
    }

    public UsersPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        UsersMessages text = AppMessages.USERS;
        header.add(new HTML(UiTemplates.HEADINGS.h1(text.title())));

        FlowPanel actions = new FlowPanel();
        actions.setStyleName("row-actions");
        AutoRefresh refresh = new AutoRefresh(new AutoRefresh.Callback() {
            @Override
            public void onRefresh() {
                load();
            }
        });
        shell.attachRefresh(refresh);
        Button create = new Button(text.addUser());
        create.setStyleName("primary");
        create.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showUserForm(null);
            }
        });
        actions.add(refresh);
        actions.add(create);
        header.add(actions);

        Label hint = new Label(text.rolesHint());
        hint.setStyleName("hint");

        tableHost.setStyleName("table-wrap");

        FlowPanel logPanel = new FlowPanel();
        logPanel.setStyleName("panel form");
        FlowPanel logHeader = new FlowPanel();
        logHeader.setStyleName("row-between");
        logHeader.add(new HTML(UiTemplates.HEADINGS.h2(text.recentConnections())));
        AutoRefresh logRefresh = new AutoRefresh(new AutoRefresh.Callback() {
            @Override
            public void onRefresh() {
                loadConnections();
            }
        });
        shell.attachRefresh(logRefresh);
        logHeader.add(logRefresh);
        logPanel.add(logHeader);
        connectionTable.setStyleName("table-wrap");
        logPanel.add(connectionTable);

        root.add(header);
        // root.add(hint);
        root.add(tableHost);
        root.add(logPanel);
        initWidget(root);
        load();
        loadConnections();
    }

    private void load() {
        shell.getRpc().getUsers(new AsyncCallback<UsersPageGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(UsersPageGWTDTO result) {
                render(result);
            }
        });
    }

    private void render(UsersPageGWTDTO page) {
        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        UsersMessages text = AppMessages.USERS;
        Tables.header(table,
                text.colUsername(), text.colRoles(), text.colStatus(), text.colConn(), text.colSpeed(),
                text.colExpires(), text.colTraffic(), text.colLastUsed(), text.colActions());

        int row = 1;
        if (page.getUsers() != null) {
            for (final UserRowGWTDTO user : page.getUsers()) {
                if (user.isRoleAdmin()) {
                    table.getRowFormatter().addStyleName(row, "admin-row");
                }
                table.setText(row, 0, nullToEmpty(user.getId()));
                table.getCellFormatter().addStyleName(row, 0, "cell-ellipsis");
                table.setHTML(row, 1, rolesBadge(user));
                table.setHTML(row, 2, statusBadge(user));
                table.setHTML(row, 3, connCell(user));
                table.getCellFormatter().addStyleName(row, 3, "num");
                boolean live = user.getUpBps() > 0 || user.getDownBps() > 0;
                table.setHTML(row, 4, TEMPLATES.speed(
                        Formatters.formatRate(user.getUpBps()),
                        Formatters.formatRateLimit(user.getSpeedLimitUpBps()),
                        Formatters.formatRate(user.getDownBps()),
                        Formatters.formatRateLimit(user.getSpeedLimitDownBps())));
                table.getCellFormatter().setStyleName(row, 4, live ? "speed live" : "speed");
                table.setText(row, 5, Formatters.never(user.getExpiresAt()));
                table.getCellFormatter().addStyleName(row, 5, "num");
                long used = user.getBytesUp() + user.getBytesDown();
                table.setHTML(row, 6, TEMPLATES.traffic(
                        Formatters.formatBytes(user.getBytesUp()),
                        Formatters.formatBytes(user.getBytesDown()),
                        Formatters.formatBytes(used),
                        Formatters.formatLimit(user.getTrafficLimitBytes())));
                table.setText(row, 7, Formatters.dash(user.getLastUsedAt()));
                table.getCellFormatter().addStyleName(row, 7, "num");
                table.setWidget(row, 8, actionsFor(user));
                row++;
            }
        }

        if (row == 1) {
            table.setText(1, 0, AppMessages.USERS.noAccounts());
            table.getFlexCellFormatter().setColSpan(1, 0, 9);
            table.getCellFormatter().setStyleName(1, 0, "empty");
        }

        CellCopy.install(table);
        tableHost.add(table);
    }

    private void loadConnections() {
        shell.getRpc().getUserConnectionLog(new AsyncCallback<List<UserConnectionRowGWTDTO>>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(List<UserConnectionRowGWTDTO> result) {
                renderConnections(result);
            }
        });
    }

    private void renderConnections(List<UserConnectionRowGWTDTO> rows) {
        connectionTable.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table query-log");
        UsersMessages text = AppMessages.USERS;
        String[] headers = {
                text.colTime(), text.colUser(), text.colProto(), text.colClient(),
                text.colUp(), text.colDown(), text.colResult(), text.colElapsed()
        };
        Tables.header(table, headers);
        if (rows == null || rows.isEmpty()) {
            table.setText(1, 0, text.noConnections());
            table.getFlexCellFormatter().setColSpan(1, 0, headers.length);
            table.getCellFormatter().setStyleName(1, 0, "empty");
            connectionTable.add(table);
            return;
        }
        int row = 1;
        for (UserConnectionRowGWTDTO session : rows) {
            table.setText(row, 0, nullToEmpty(session.getTime()));
            table.setText(row, 1, nullToEmpty(session.getUser()));
            table.setText(row, 2, nullToEmpty(session.getProtocol()));
            table.setText(row, 3, nullToEmpty(session.getClient()));
            table.setText(row, 4, Formatters.formatBytes(session.getBytesUp()));
            table.setText(row, 5, Formatters.formatBytes(session.getBytesDown()));
            table.setText(row, 6, nullToEmpty(session.getOutcome()));
            table.setText(row, 7, nullToEmpty(session.getElapsed()));
            row++;
        }
        CellCopy.install(table);
        connectionTable.add(table);
    }

    private static SafeHtml rolesBadge(UserRowGWTDTO user) {
        UsersMessages text = AppMessages.USERS;
        if (user.isRoleAdmin() && user.isRoleUser()) {
            return TEMPLATES.adminAndUser(text.roleAdmin(), text.roleUser());
        }
        if (user.isRoleAdmin()) {
            return TEMPLATES.adminOnly(text.roleAdmin());
        }
        if (user.isRoleUser()) {
            return TEMPLATES.userOnly(text.roleUser());
        }
        return TEMPLATES.none(AppMessages.COMMON.dash());
    }

    private static SafeHtml statusBadge(UserRowGWTDTO user) {
        UsersMessages text = AppMessages.USERS;
        if (user.isUsable()) {
            return TEMPLATES.active(text.statusActive());
        }
        if (!user.isEnabled()) {
            return TEMPLATES.muted(text.statusDisabled());
        }
        if (user.isExpired()) {
            return TEMPLATES.warn(text.statusExpired());
        }
        if (user.isTrafficLimitExceeded()) {
            return TEMPLATES.warn(text.statusQuota());
        }
        return TEMPLATES.muted(text.statusInactive());
    }

    private static SafeHtml connCell(UserRowGWTDTO user) {
        String limit = user.getMaxConnections() > 0
                ? String.valueOf(user.getMaxConnections())
                : AppMessages.COMMON.infinity();
        return TEMPLATES.connections(user.getActiveConnections(), limit);
    }

    private FlowPanel actionsFor(final UserRowGWTDTO user) {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");

        UsersMessages text = AppMessages.USERS;
        Button edit = new Button(AppMessages.COMMON.edit());
        edit.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showUserForm(user.getId());
            }
        });
        Button ssh = new Button(text.ssh());
        ssh.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showSshAccess(user.getId());
            }
        });

        Button reset = new Button(text.resetTraffic());
        reset.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (Window.confirm(text.resetTrafficConfirm(user.getId()))) {
                    shell.getRpc().resetUserTraffic(user.getId(), voidReload(text.trafficReset()));
                }
            }
        });

        actions.add(edit);
        actions.add(ssh);
        Button toggle = new Button(user.isEnabled() ? text.disable() : text.enable());
        toggle.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.getRpc().setUserEnabled(user.getId(), !user.isEnabled(), voidReload(
                        user.isEnabled() ? text.userDisabled() : text.userEnabled()));
            }
        });
        actions.add(toggle);
        actions.add(reset);

        if (!user.isBootstrapAdmin()) {
            Button delete = new Button(AppMessages.COMMON.delete());
            delete.setStyleName("danger");
            delete.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    if (Window.confirm(text.deleteUserConfirm())) {
                        shell.getRpc().deleteUser(user.getId(), voidReload(text.userDeleted()));
                    }
                }
            });
            actions.add(delete);
        }
        return actions;
    }

    private AsyncCallback<Void> voidReload(final String okMessage) {
        return new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash(okMessage, true);
                load();
            }
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
