package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeaderRowDto;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeadersPageDto;
import io.github.connellite.proxy.client.util.Rpc;

public class HttpStripHeadersPage extends Composite {

    private final AppShell shell;
    private final FlowPanel tableHost = new FlowPanel();
    private final TextBox nameInput = new TextBox();

    public HttpStripHeadersPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        header.add(new HTML("<h1>Strip headers</h1>"));

        Label hint = new Label("Headers removed from plain HTTP proxy requests before forwarding. "
                + "Does not apply to HTTPS CONNECT tunnels. Changes take effect immediately.");
        hint.setStyleName("hint");
        tableHost.setStyleName("table-wrap");

        nameInput.getElement().setAttribute("placeholder", "Header name, e.g. Via");
        nameInput.setWidth("100%");
        nameInput.addKeyDownHandler(new KeyDownHandler() {
            @Override
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                    addHeader();
                }
            }
        });

        root.add(header);
        root.add(hint);
        root.add(tableHost);
        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getHttpStripHeaders(new AsyncCallback<>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(HttpStripHeadersPageDto result) {
                render(result);
            }
        });
    }

    private void render(HttpStripHeadersPageDto page) {
        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table strip-headers-table");

        table.setText(0, 0, "Name");
        table.setText(0, 1, "Action");
        styleHeaderCell(table, 0, 0);
        styleHeaderCell(table, 0, 1);
        table.getColumnFormatter().setWidth(0, "75%");
        table.getColumnFormatter().setWidth(1, "25%");

        int row = 1;
        if (page.getHeaders() != null) {
            for (final HttpStripHeaderRowDto header : page.getHeaders()) {
                table.setText(row, 0, header.getName() == null ? "" : header.getName());
                table.getCellFormatter().addStyleName(row, 0, "cell-ellipsis");
                table.setWidget(row, 1, minusButton(header));
                row++;
            }
        }

        table.setWidget(row, 0, nameInput);
        table.setWidget(row, 1, plusButton());

        tableHost.add(table);
        nameInput.setFocus(true);
    }

    private FlowPanel plusButton() {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");
        Button add = new Button("+");
        add.setStyleName("btn-icon success");
        add.setTitle("Add header");
        add.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                addHeader();
            }
        });
        actions.add(add);
        return actions;
    }

    private FlowPanel minusButton(final HttpStripHeaderRowDto header) {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");
        Button remove = new Button("−");
        remove.setStyleName("btn-icon danger-fill");
        remove.setTitle("Remove " + header.getName());
        remove.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.getRpc().deleteHttpStripHeader(header.getId(), voidReload("Removed \"" + header.getName() + "\""));
            }
        });
        actions.add(remove);
        return actions;
    }

    private void addHeader() {
        final String name = nameInput.getText() == null ? "" : nameInput.getText().trim();
        if (name.isEmpty()) {
            shell.showFlash("Enter a header name", false);
            return;
        }
        shell.getRpc().addHttpStripHeader(name, new AsyncCallback<>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                nameInput.setText("");
                shell.showFlash("Added \"" + name + "\"", true);
                load();
            }
        });
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

    private static void styleHeaderCell(FlexTable table, int row, int col) {
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("background", "#efe8dc");
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("fontSize", "0.75rem");
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("textTransform", "uppercase");
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("fontWeight", "700");
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("color", "#6b645a");
    }
}
