package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.SshMessages;
import io.github.connellite.proxy.client.rpc.dto.SshAccessGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshIssuedKeyGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshUserKeyRowGWTDTO;
import io.github.connellite.proxy.client.util.CellCopy;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.Rpc;
import io.github.connellite.proxy.client.util.Tables;

public class SshAccessPage extends Composite {

    private final AppShell shell;
    private final String userId;
    private final FlowPanel tableHost = new FlowPanel();
    private final SshMessages text = AppMessages.SSH;
    private final CheckBox passwordEnabled = Forms.checkbox(text.allowPassword());
    private final TextBox comment = new TextBox();
    private final PasswordTextBox passphrase = new PasswordTextBox();
    private final FlowPanel issuedPanel = new FlowPanel();
    private final Label issuedWarning = new Label();
    private final TextArea privateKey = new TextArea();

    public SshAccessPage(AppShell shell, String userId) {
        this.shell = shell;
        this.userId = userId;

        comment.getElement().setAttribute("maxlength", "256");
        passphrase.getElement().setAttribute("maxlength", "128");
        privateKey.setReadOnly(true);
        privateKey.setVisibleLines(14);
        privateKey.setWidth("100%");

        FlowPanel root = new FlowPanel();
        root.add(new HTML(UiTemplates.HEADINGS.h1(text.title())));
//        Label userLabel = new Label(userId == null ? "" : userId);
//        userLabel.setStyleName("hint");
//        root.add(userLabel);

        FlowPanel passwordPanel = new FlowPanel();
        passwordPanel.setStyleName("panel form");
        passwordPanel.add(passwordEnabled);
        Label passwordHint = new Label(text.passwordHint());
        passwordHint.setStyleName("field-hint");
        passwordPanel.add(passwordHint);
        Button savePassword = new Button(AppMessages.COMMON.save());
        savePassword.setStyleName("primary");
        savePassword.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                savePassword();
            }
        });
        passwordPanel.add(Forms.formActions(savePassword));
        root.add(passwordPanel);

        root.add(new HTML(UiTemplates.HEADINGS.h2(text.keys())));
        Label keysHint = new Label(text.keysHint());
        keysHint.setStyleName("hint");
        root.add(keysHint);
        tableHost.setStyleName("table-wrap");
        root.add(tableHost);

        FlowPanel issuePanel = new FlowPanel();
        issuePanel.setStyleName("panel form");
        issuePanel.add(new HTML(UiTemplates.HEADINGS.h2(text.issueKey())));
        issuePanel.add(Forms.field(text.comment(), comment, text.commentHint()));
        issuePanel.add(Forms.field(text.passphrase(), passphrase, text.passphraseHint()));
        Button issue = new Button(text.issue());
        issue.setStyleName("primary");
        issue.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                issueKey();
            }
        });
        issuePanel.add(Forms.formActions(issue));
        root.add(issuePanel);

        issuedPanel.setStyleName("panel form");
        issuedPanel.setVisible(false);
        issuedPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.privateKey())));
        issuedWarning.setStyleName("hint");
        issuedPanel.add(issuedWarning);
        issuedPanel.add(privateKey);
        Button download = new Button(text.download());
        download.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                downloadPrivateKey();
            }
        });
        Button back = new Button(text.backToUsers());
        back.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showUsers();
            }
        });
        issuedPanel.add(Forms.formActions(download));
        root.add(issuedPanel);

        FlowPanel footer = new FlowPanel();
        footer.add(Forms.formActions(back));
        root.add(footer);

        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getSshAccess(userId, new AsyncCallback<SshAccessGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(SshAccessGWTDTO result) {
                passwordEnabled.setValue(result.isSshPasswordEnabled());
                renderKeys(result);
            }
        });
    }

    private void renderKeys(SshAccessGWTDTO page) {
        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        Tables.header(table, text.colComment(), text.colFingerprint(), text.colCreated(), text.colActions());

        int row = 1;
        if (page.getKeys() != null) {
            for (final SshUserKeyRowGWTDTO key : page.getKeys()) {
                table.setText(row, 0, blankToDash(key.getComment()));
                table.getCellFormatter().addStyleName(row, 0, "cell-ellipsis");
                table.setText(row, 1, blankToDash(key.getFingerprint()));
                table.getCellFormatter().addStyleName(row, 1, "cell-ellipsis");
                table.setText(row, 2, blankToDash(key.getCreatedAt()));
                table.setWidget(row, 3, revokeButton(key));
                row++;
            }
        }
        if (row == 1) {
            table.setText(1, 0, text.noKeys());
            table.getFlexCellFormatter().setColSpan(1, 0, 4);
            table.getCellFormatter().setStyleName(1, 0, "empty");
        }
        CellCopy.install(table);
        tableHost.add(table);
    }

    private FlowPanel revokeButton(final SshUserKeyRowGWTDTO key) {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");
        Button revoke = new Button(text.revoke());
        revoke.setStyleName("danger");
        revoke.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                String label = key.getFingerprint() == null ? text.thisKey() : key.getFingerprint();
                if (Window.confirm(text.revokeConfirm(label))) {
                    shell.getRpc().revokeSshKey(userId, key.getId(), new AsyncCallback<Void>() {
                        @Override
                        public void onFailure(Throwable caught) {
                            Rpc.showFailure(caught);
                        }

                        @Override
                        public void onSuccess(Void result) {
                            shell.showFlash(text.keyRevoked(), true);
                            load();
                        }
                    });
                }
            }
        });
        actions.add(revoke);
        return actions;
    }

    private void savePassword() {
        final boolean enabled = passwordEnabled.getValue();
        shell.getRpc().setSshPasswordEnabled(userId, enabled, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash(enabled ? text.passwordEnabled() : text.passwordDisabled(), true);
            }
        });
    }

    private void issueKey() {
        final String commentText = comment.getText();
        final String passphraseText = passphrase.getText();
        shell.getRpc().issueSshKey(userId, commentText, passphraseText, new AsyncCallback<SshIssuedKeyGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(SshIssuedKeyGWTDTO result) {
                comment.setText("");
                passphrase.setText("");
                showIssued(result);
                shell.showFlash(text.keyIssued(), true);
                load();
            }
        });
    }

    private void showIssued(SshIssuedKeyGWTDTO issued) {
        StringBuilder warning = new StringBuilder();
        warning.append(text.shownOnce());
        if (issued.isEncrypted()) {
            warning.append(" ").append(text.encryptedFile());
        }
        if (issued.getFingerprint() != null && !issued.getFingerprint().isEmpty()) {
            warning.append(" ").append(text.fingerprint(issued.getFingerprint()));
        }
        issuedWarning.setText(warning.toString());
        privateKey.setText(issued.getPrivateKey() == null ? "" : issued.getPrivateKey());
        issuedPanel.setVisible(true);
    }

    private void downloadPrivateKey() {
        String content = privateKey.getText();
        if (content == null || content.isEmpty()) {
            return;
        }
        downloadText(fileName(), content);
    }

    private String fileName() {
        String safe = userId == null ? "user" : userId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty()) {
            safe = "user";
        }
        return safe + "-ed25519";
    }

    private static String blankToDash(String value) {
        return value == null || value.isEmpty() ? AppMessages.COMMON.dash() : value;
    }

    private static native void downloadText(String filename, String content) /*-{
        var blob = new $wnd.Blob([content], {type: "text/plain;charset=utf-8"});
        var url = $wnd.URL.createObjectURL(blob);
        var anchor = $wnd.document.createElement("a");
        anchor.href = url;
        anchor.download = filename;
        $wnd.document.body.appendChild(anchor);
        anchor.click();
        $wnd.document.body.removeChild(anchor);
        $wnd.URL.revokeObjectURL(url);
    }-*/;
}
