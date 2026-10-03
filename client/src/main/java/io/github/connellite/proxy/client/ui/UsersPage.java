package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import io.github.connellite.proxy.client.rpc.dto.UserRowDto;
import io.github.connellite.proxy.client.rpc.dto.UsersPageDto;
import io.github.connellite.proxy.client.util.AutoRefresh;
import io.github.connellite.proxy.client.util.Formatters;
import io.github.connellite.proxy.client.util.Rpc;

public class UsersPage extends Composite {

    private final AppShell shell;
    private final FlowPanel tableHost = new FlowPanel();

    public UsersPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        header.add(new HTML("<h1>Users</h1>"));

        FlowPanel actions = new FlowPanel();
        actions.setStyleName("row-actions");
        AutoRefresh refresh = new AutoRefresh(new AutoRefresh.Callback() {
            @Override
            public void onRefresh() {
                load();
            }
        });
        shell.attachRefresh(refresh);
        Button create = new Button("Add user");
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

        Label hint = new Label("Admin can sign in to this UI; User can use the proxy. "
                + "Accounts may have either, both, or neither role.");
        hint.setStyleName("hint");

        tableHost.setStyleName("table-wrap");

        root.add(header);
        // root.add(hint);
        root.add(tableHost);
        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getUsers(new AsyncCallback<UsersPageDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(UsersPageDto result) {
                render(result);
            }
        });
    }

    private void render(UsersPageDto page) {
        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        String[] headers = {
                "Username", "Roles", "Status", "Conn", "Speed", "Expires", "Traffic", "Last used", "Actions"
        };
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
            table.getCellFormatter().setStyleName(0, i, "");
        }
        table.getRowFormatter().setStyleName(0, "");
        for (int i = 0; i < headers.length; i++) {
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("background", "#efe8dc");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("fontSize", "0.75rem");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("textTransform", "uppercase");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("fontWeight", "700");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("color", "#6b645a");
        }

        int row = 1;
        if (page.getUsers() != null) {
            for (final UserRowDto user : page.getUsers()) {
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
                table.setHTML(row, 4,
                        "<div class=\"metric-line\">↑ <span class=\"num\">"
                                + Formatters.formatRate(user.getUpBps())
                                + "</span> <span class=\"muted-cell\">/ "
                                + Formatters.formatRateLimit(user.getSpeedLimitUpBps())
                                + "</span></div>"
                                + "<div class=\"metric-line\">↓ <span class=\"num\">"
                                + Formatters.formatRate(user.getDownBps())
                                + "</span> <span class=\"muted-cell\">/ "
                                + Formatters.formatRateLimit(user.getSpeedLimitDownBps())
                                + "</span></div>");
                table.getCellFormatter().setStyleName(row, 4, live ? "speed live" : "speed");
                table.setText(row, 5, Formatters.never(user.getExpiresAt()));
                table.getCellFormatter().addStyleName(row, 5, "num");
                long used = user.getBytesUp() + user.getBytesDown();
                table.setHTML(row, 6,
                        "<div class=\"metric-line traffic\">↑ <span class=\"num\">"
                                + Formatters.formatBytes(user.getBytesUp()) + "</span></div>"
                                + "<div class=\"metric-line\">↓ <span class=\"num\">"
                                + Formatters.formatBytes(user.getBytesDown()) + "</span></div>"
                                + "<div class=\"metric-line muted-cell\">Σ "
                                + Formatters.formatBytes(used) + " / "
                                + Formatters.formatLimit(user.getTrafficLimitBytes()) + "</div>");
                table.setText(row, 7, Formatters.dash(user.getLastUsedAt()));
                table.getCellFormatter().addStyleName(row, 7, "num");
                table.setWidget(row, 8, actionsFor(user));
                row++;
            }
        }

        if (row == 1) {
            table.setText(1, 0, "No accounts yet.");
            table.getFlexCellFormatter().setColSpan(1, 0, 9);
            table.getCellFormatter().setStyleName(1, 0, "empty");
        }

        tableHost.add(table);
    }

    private static String rolesBadge(UserRowDto user) {
        StringBuilder badges = new StringBuilder();
        if (user.isRoleAdmin()) {
            badges.append("<span class=\"badge admin\">Admin</span> ");
        }
        if (user.isRoleUser()) {
            badges.append("<span class=\"badge\">User</span> ");
        }
        if (badges.length() == 0) {
            return "<span class=\"muted-cell\">—</span>";
        }
        return badges.toString().trim();
    }

    private static String statusBadge(UserRowDto user) {
        if (user.isUsable()) {
            return "<span class=\"badge ok\">active</span>";
        }
        if (!user.isEnabled()) {
            return "<span class=\"badge muted\">disabled</span>";
        }
        if (user.isExpired()) {
            return "<span class=\"badge warn\">expired</span>";
        }
        if (user.isTrafficLimitExceeded()) {
            return "<span class=\"badge warn\">quota</span>";
        }
        return "<span class=\"badge muted\">inactive</span>";
    }

    private static String connCell(UserRowDto user) {
        String limit = user.getMaxConnections() > 0
                ? " / " + user.getMaxConnections()
                : " / ∞";
        return "<span>" + user.getActiveConnections() + "</span><span>" + limit + "</span>";
    }

    private FlowPanel actionsFor(final UserRowDto user) {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");

        Button edit = new Button("Edit");
        edit.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showUserForm(user.getId());
            }
        });
        Button ssh = new Button("SSH");
        ssh.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showSshAccess(user.getId());
            }
        });

        Button reset = new Button("Reset traffic");
        reset.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (Window.confirm("Reset traffic counters for " + user.getId() + "?")) {
                    shell.getRpc().resetUserTraffic(user.getId(), voidReload("Traffic counters reset"));
                }
            }
        });

        actions.add(edit);
        actions.add(ssh);
        if (!user.isBootstrapAdmin()) {
            Button toggle = new Button(user.isEnabled() ? "Disable" : "Enable");
            toggle.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    shell.getRpc().setUserEnabled(user.getId(), !user.isEnabled(), voidReload(
                            user.isEnabled() ? "User disabled" : "User enabled"));
                }
            });
            actions.add(toggle);
        }
        actions.add(reset);

        if (!user.isBootstrapAdmin()) {
            Button delete = new Button("Delete");
            delete.setStyleName("danger");
            delete.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    if (Window.confirm("Delete this user?")) {
                        shell.getRpc().deleteUser(user.getId(), voidReload("User deleted"));
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
