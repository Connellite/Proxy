package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
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
import io.github.connellite.proxy.client.rpc.dto.SftpPageDto;
import io.github.connellite.proxy.client.rpc.dto.SftpUserRowDto;
import io.github.connellite.proxy.client.util.Rpc;

/**
 * Per-user SFTP folders. The folder becomes the user's {@code /} over SFTP, either read-only or
 * read/write; users without a folder are refused. Editing is available only while the SSH proxy is enabled.
 */
public class SftpPage extends Composite {

    private final AppShell shell;
    private final Label notice = new Label();
    private final FlowPanel tableHost = new FlowPanel();

    public SftpPage(AppShell shell) {
        this.shell = shell;

        FlowPanel root = new FlowPanel();
        root.add(new HTML("<h1>SFTP</h1>"));
        Label hint = new Label("Pick a server folder for each user. Over SFTP that folder is the user's "
                + "root (/): subfolders are visible, nothing above it is. Read only lets the user list and download; "
                + "otherwise the user can also upload, rename and delete. "
                + "Users without a folder cannot use SFTP. Changes apply to new connections.");
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
            public void onSuccess(SftpPageDto result) {
                render(result);
            }
        });
    }

    private void render(SftpPageDto page) {
        boolean editable = page.isSshEnabled();
        if (!page.isSshEnabled()) {
            notice.setText("The SSH proxy is disabled. Enable it in Settings to use SFTP.");
            notice.setVisible(true);
        } else if (!page.isSshRunning()) {
            notice.setText("The SSH proxy is enabled but not running. Check the last error on the Dashboard.");
            notice.setVisible(true);
        } else {
            notice.setText("");
            notice.setVisible(false);
        }

        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table sftp-table");
        String[] headers = {"User", "SFTP folder", "Access", "Actions"};
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
        }
        table.getRowFormatter().setStyleName(0, "sftp-head");

        int row = 1;
        if (page.getUsers() != null) {
            for (SftpUserRowDto user : page.getUsers()) {
                addRow(table, row++, user, editable);
            }
        }
        if (row == 1) {
            table.setText(1, 0, "No users yet.");
            table.getFlexCellFormatter().setColSpan(1, 0, headers.length);
            table.getCellFormatter().setStyleName(1, 0, "empty");
        }
        tableHost.add(table);
    }

    private void addRow(FlexTable table, int row, final SftpUserRowDto user, boolean editable) {
        String status;
        if (user.getRoot() == null) {
            status = " <span class=\"badge muted\">off</span>";
        } else if (!user.isUsable()) {
            status = " <span class=\"badge warn\">user inactive</span>";
        } else if (user.isReadOnly()) {
            status = " <span class=\"badge\">read only</span>";
        } else {
            status = " <span class=\"badge ok\">read/write</span>";
        }
        table.setHTML(row, 0, "<span>" + escape(user.getUserId()) + "</span>" + status);

        final CheckBox readOnly = new CheckBox("Read only");
        readOnly.setValue(user.getRoot() != null && user.isReadOnly());
        readOnly.setTitle("List and download only: no upload, rename, delete or mkdir");
        readOnly.setEnabled(editable);

        final TextBox folder = new TextBox();
        folder.setText(user.getRoot() == null ? "" : user.getRoot());
        folder.getElement().setAttribute("placeholder", "Not set: SFTP is off for this user");
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
        Button browse = new Button("Browse…");
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
        Button save = new Button("Save");
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                save(user.getUserId(), folder.getText(), readOnly.getValue());
            }
        });
        Button clear = new Button("Disable");
        clear.setStyleName("danger");
        clear.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (Window.confirm("Turn off SFTP for " + user.getUserId() + "? Files on the server are kept.")) {
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
                shell.showFlash("SFTP turned off for " + userId, true);
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
                shell.showFlash(clearing
                        ? "SFTP turned off for " + userId
                        : "SFTP access saved for " + userId + (readOnly ? " (read only)" : " (read/write)"), true);
                load();
            }
        });
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
