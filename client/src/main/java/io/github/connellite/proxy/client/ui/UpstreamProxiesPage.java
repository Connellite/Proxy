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
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyRowDto;
import io.github.connellite.proxy.client.util.Rpc;

public class UpstreamProxiesPage extends Composite {

    private final AppShell shell;
    private final FlowPanel tableHost = new FlowPanel();
    private final Label status = new Label();

    public UpstreamProxiesPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        header.add(new HTML("<h1>Upstream</h1>"));

        FlowPanel actions = new FlowPanel();
        actions.setStyleName("row-actions");
        Button clear = new Button("Use direct");
        clear.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.getRpc().clearUpstreamProxySelection(voidReload("Using direct outbound connections"));
            }
        });
        Button create = new Button("Add upstream");
        create.setStyleName("primary");
        create.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showUpstreamForm(null);
            }
        });
        actions.add(clear);
        actions.add(create);
        header.add(actions);

        Label hint = new Label("Configure parent proxies for cascading. Only one can be selected; "
                + "\"Use direct\" disables cascading.");
        hint.setStyleName("hint");
        status.setStyleName("hint");
        tableHost.setStyleName("table-wrap");

        root.add(header);
        root.add(hint);
        root.add(status);
        root.add(tableHost);
        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getUpstreamProxies(new AsyncCallback<>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(UpstreamProxiesPageDto result) {
                render(result);
            }
        });
    }

    private void render(UpstreamProxiesPageDto page) {
        if (page.getSelectedId() == 0L) {
            status.setText("Active: direct (no upstream)");
        } else {
            String selectedName = "";
            if (page.getProxies() != null) {
                for (UpstreamProxyRowDto row : page.getProxies()) {
                    if (row.isSelected()) {
                        selectedName = row.getName();
                        break;
                    }
                }
            }
            status.setText("Active: " + selectedName);
        }

        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        String[] headers = {"Name", "Type", "Endpoint", "Auth", "Status", "Actions"};
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("background", "#efe8dc");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("fontSize", "0.75rem");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("textTransform", "uppercase");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("fontWeight", "700");
            table.getCellFormatter().getElement(0, i).getStyle().setProperty("color", "#6b645a");
        }

        int row = 1;
        if (page.getProxies() != null) {
            for (final UpstreamProxyRowDto proxy : page.getProxies()) {
                table.setText(row, 0, nullToEmpty(proxy.getName()));
                table.getCellFormatter().addStyleName(row, 0, "cell-ellipsis");
                table.setText(row, 1, nullToEmpty(proxy.getType()));
                table.setText(row, 2, nullToEmpty(proxy.getHost()) + ":" + proxy.getPort());
                table.getCellFormatter().addStyleName(row, 2, "num");
                table.setHTML(row, 3, proxy.isAuthEnabled()
                        ? "<span class=\"badge ok\">yes</span>"
                        : "<span class=\"badge muted\">no</span>");
                table.setHTML(row, 4, proxy.isSelected()
                        ? "<span class=\"badge ok\">selected</span>"
                        : "<span class=\"badge muted\">idle</span>");
                table.setWidget(row, 5, actionsFor(proxy));
                row++;
            }
        }

        if (row == 1) {
            table.setText(1, 0, "No upstream proxies yet.");
            table.getFlexCellFormatter().setColSpan(1, 0, 6);
            table.getCellFormatter().setStyleName(1, 0, "empty");
        }

        tableHost.add(table);
    }

    private FlowPanel actionsFor(final UpstreamProxyRowDto proxy) {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");

        if (!proxy.isSelected()) {
            Button select = new Button("Select");
            select.setStyleName("primary");
            select.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    shell.getRpc().selectUpstreamProxy(proxy.getId(),
                            voidReload("Upstream \"" + proxy.getName() + "\" selected"));
                }
            });
            actions.add(select);
        }

        Button edit = new Button("Edit");
        edit.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showUpstreamForm(proxy.getId());
            }
        });

        Button delete = new Button("Delete");
        delete.setStyleName("danger");
        delete.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (Window.confirm("Delete upstream proxy \"" + proxy.getName() + "\"?")) {
                    shell.getRpc().deleteUpstreamProxy(proxy.getId(), voidReload("Upstream deleted"));
                }
            }
        });

        actions.add(edit);
        actions.add(delete);
        return actions;
    }

    private AsyncCallback<Void> voidReload(final String okMessage) {
        return new AsyncCallback<>() {
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
