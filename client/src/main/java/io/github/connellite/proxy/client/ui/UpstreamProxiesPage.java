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
import io.github.connellite.proxy.client.messages.UpstreamMessages;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyRowGWTDTO;
import io.github.connellite.proxy.client.util.CellCopy;
import io.github.connellite.proxy.client.util.Rpc;
import io.github.connellite.proxy.client.util.Tables;

public class UpstreamProxiesPage extends Composite {

    private final AppShell shell;
    private final FlowPanel tableHost = new FlowPanel();
    private final Label status = new Label();
    private static final Templates TEMPLATES = GWT.create(Templates.class);

    interface Templates extends SafeHtmlTemplates {
        @Template("<span class=\"badge ok\">{0}</span>")
        SafeHtml ok(String text);

        @Template("<span class=\"badge muted\">{0}</span>")
        SafeHtml muted(String text);
    }

    public UpstreamProxiesPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        UpstreamMessages text = AppMessages.UPSTREAM;
        header.add(new HTML(UiTemplates.HEADINGS.h1(text.title())));

        FlowPanel actions = new FlowPanel();
        actions.setStyleName("row-actions");
        Button clear = new Button(text.useDirect());
        clear.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.getRpc().clearUpstreamProxySelection(voidReload(text.usingDirect()));
            }
        });
        Button create = new Button(text.addUpstream());
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

        Label hint = new Label(text.hint());
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
            public void onSuccess(UpstreamProxiesPageGWTDTO result) {
                render(result);
            }
        });
    }

    private void render(UpstreamProxiesPageGWTDTO page) {
        UpstreamMessages text = AppMessages.UPSTREAM;
        if (page.getSelectedId() == 0L) {
            status.setText(text.activeDirect());
        } else {
            String selectedName = "";
            if (page.getProxies() != null) {
                for (UpstreamProxyRowGWTDTO row : page.getProxies()) {
                    if (row.isSelected()) {
                        selectedName = row.getName();
                        break;
                    }
                }
            }
            status.setText(text.activeNamed(selectedName));
        }

        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        Tables.header(table,
                text.colName(), text.colType(), text.colEndpoint(), text.colAuth(), text.colStatus(), text.colActions());

        int row = 1;
        if (page.getProxies() != null) {
            for (final UpstreamProxyRowGWTDTO proxy : page.getProxies()) {
                table.setText(row, 0, nullToEmpty(proxy.getName()));
                table.getCellFormatter().addStyleName(row, 0, "cell-ellipsis");
                table.setText(row, 1, AppMessages.upstreamType(proxy.getType()));
                table.setText(row, 2, nullToEmpty(proxy.getHost()) + ":" + proxy.getPort());
                table.getCellFormatter().addStyleName(row, 2, "num");
                table.setHTML(row, 3, proxy.isAuthEnabled()
                        ? TEMPLATES.ok(AppMessages.COMMON.yes())
                        : TEMPLATES.muted(AppMessages.COMMON.no()));
                table.setHTML(row, 4, proxy.isSelected()
                        ? TEMPLATES.ok(text.selected())
                        : TEMPLATES.muted(text.idle()));
                table.setWidget(row, 5, actionsFor(proxy));
                row++;
            }
        }

        if (row == 1) {
            table.setText(1, 0, text.noProxies());
            table.getFlexCellFormatter().setColSpan(1, 0, 6);
            table.getCellFormatter().setStyleName(1, 0, "empty");
        }

        CellCopy.install(table);
        tableHost.add(table);
    }

    private FlowPanel actionsFor(final UpstreamProxyRowGWTDTO proxy) {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");

        if (!proxy.isSelected()) {
            Button select = new Button(AppMessages.COMMON.select());
            select.setStyleName("primary");
            select.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    shell.getRpc().selectUpstreamProxy(proxy.getId(),
                            voidReload(AppMessages.UPSTREAM.upstreamSelected(proxy.getName())));
                }
            });
            actions.add(select);
        }

        Button edit = new Button(AppMessages.COMMON.edit());
        edit.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showUpstreamForm(proxy.getId());
            }
        });

        Button delete = new Button(AppMessages.COMMON.delete());
        delete.setStyleName("danger");
        delete.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (Window.confirm(AppMessages.UPSTREAM.deleteConfirm(proxy.getName()))) {
                    shell.getRpc().deleteUpstreamProxy(proxy.getId(), voidReload(AppMessages.UPSTREAM.deleted()));
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
