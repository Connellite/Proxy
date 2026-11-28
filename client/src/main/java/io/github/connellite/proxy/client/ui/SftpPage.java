package io.github.connellite.proxy.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.client.SafeHtmlTemplates.Template;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.SftpMessages;
import io.github.connellite.proxy.client.rpc.dto.SftpPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SftpUserRowGWTDTO;
import io.github.connellite.proxy.client.util.CellCopy;
import io.github.connellite.proxy.client.util.Rpc;
import io.github.connellite.proxy.client.util.Tables;

/**
 * Per-user SFTP folders. The folder becomes the user's {@code /} over SFTP, either read-only or
 * read/write; users without a folder are refused. Editing is available only while the SSH proxy is enabled.
 */
public class SftpPage extends Composite {

    private final AppShell shell;
    private final Label notice = new Label();
    private final FlowPanel tableHost = new FlowPanel();
    private static final Templates TEMPLATES = GWT.create(Templates.class);

    interface Templates extends SafeHtmlTemplates {
        @Template("<span>{0}</span> <span class=\"badge muted\">{1}</span>")
        SafeHtml off(String userId, String status);

        @Template("<span>{0}</span> <span class=\"badge warn\">{1}</span>")
        SafeHtml inactive(String userId, String status);

        @Template("<span>{0}</span> <span class=\"badge\">{1}</span>")
        SafeHtml readOnly(String userId, String status);

        @Template("<span>{0}</span> <span class=\"badge ok\">{1}</span>")
        SafeHtml readWrite(String userId, String status);
    }

    public SftpPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        SftpMessages text = AppMessages.SFTP;
        root.add(new HTML(UiTemplates.HEADINGS.h1(text.title())));
        Label hint = new Label(text.hint());
        hint.setStyleName("hint");
        root.add(hint);

        notice.setStyleName("flash warn");
        notice.setVisible(false);
        root.add(notice);

        tableHost.setStyleName("table-wrap");
        root.add(tableHost);

        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getSftpAccess(new AsyncCallback<>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(SftpPageGWTDTO result) {
                render(result);
            }
        });
    }

    private void render(SftpPageGWTDTO page) {
        boolean editable = page.isSshEnabled();
        SftpMessages text = AppMessages.SFTP;
        if (!page.isSshEnabled()) {
            notice.setText(text.sshDisabled());
            notice.setVisible(true);
        } else if (!page.isSshRunning()) {
            notice.setText(text.sshNotRunning());
            notice.setVisible(true);
        } else {
            notice.setText("");
            notice.setVisible(false);
        }

        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table sftp-table");
        String[] headers = {text.colUser(), text.colFolder(), text.colAccess(), text.colActions()};
        Tables.header(table, headers);

        int row = 1;
        if (page.getUsers() != null) {
            for (SftpUserRowGWTDTO user : page.getUsers()) {
                addRow(table, row++, user, editable);
            }
        }
        if (row == 1) {
            table.setText(1, 0, text.noUsers());
            table.getFlexCellFormatter().setColSpan(1, 0, headers.length);
            table.getCellFormatter().setStyleName(1, 0, "empty");
        }
        CellCopy.install(table);
        tableHost.add(table);
    }

    private void addRow(FlexTable table, int row, final SftpUserRowGWTDTO user, boolean editable) {
        SftpMessages text = AppMessages.SFTP;
        SafeHtml status;
        if (user.getRoot() == null) {
            status = TEMPLATES.off(user.getUserId(), text.off());
        } else if (!user.isUsable()) {
            status = TEMPLATES.inactive(user.getUserId(), text.userInactive());
        } else if (user.isReadOnly()) {
            status = TEMPLATES.readOnly(user.getUserId(), text.readOnly());
        } else {
            status = TEMPLATES.readWrite(user.getUserId(), text.readWrite());
        }
        table.setHTML(row, 0, status);

        final CheckBox readOnly = new CheckBox(text.readOnly());
        readOnly.setValue(user.getRoot() != null && user.isReadOnly());
        readOnly.setTitle(text.readOnlyTitle());
        readOnly.setEnabled(editable);

        final TextBox folder = new TextBox();
        folder.setText(user.getRoot() == null ? "" : user.getRoot());
        folder.getElement().setAttribute("placeholder", text.folderPlaceholder());
        folder.getElement().setAttribute("maxlength", "1024");
        folder.setWidth("100%");
        folder.setEnabled(editable);
        folder.addKeyDownHandler(new KeyDownHandler() {
            @Override
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                    save(user.getUserId(), folder.getText(), readOnly.getValue());
                }
            }
        });
        table.setWidget(row, 1, folder);
        table.setWidget(row, 2, readOnly);

        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");
        Button browse = new Button(text.browse());
        browse.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                new DirectoryPickerDialog(shell.getRpc(), folder.getText(), new DirectoryPickerDialog.Callback() {
                    @Override
                    public void onSelected(String path) {
                        folder.setText(path);
                    }
                });
            }
        });
        Button save = new Button(AppMessages.COMMON.save());
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                save(user.getUserId(), folder.getText(), readOnly.getValue());
            }
        });
        Button clear = new Button(text.disable());
        clear.setStyleName("danger");
        clear.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (Window.confirm(text.turnOffConfirm(user.getUserId()))) {
                    remove(user.getUserId());
                }
            }
        });
        browse.setEnabled(editable);
        save.setEnabled(editable);
        clear.setEnabled(editable && user.getRoot() != null);
        actions.add(browse);
        actions.add(save);
        actions.add(clear);
        table.setWidget(row, 3, actions);
    }

    private void remove(final String userId) {
        shell.getRpc().removeSftpAccess(userId, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash(AppMessages.SFTP.turnedOff(userId), true);
                load();
            }
        });
    }

    private void save(final String userId, String path, final boolean readOnly) {
        final boolean clearing = path == null || path.trim().isEmpty();
        shell.getRpc().saveSftpAccess(userId, path, readOnly, new AsyncCallback<>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                SftpMessages text = AppMessages.SFTP;
                shell.showFlash(clearing
                        ? text.turnedOff(userId)
                        : (readOnly ? text.savedReadOnly(userId) : text.savedReadWrite(userId)), true);
                load();
            }
        });
    }
}
