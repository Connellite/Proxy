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
import io.github.connellite.proxy.client.rpc.dto.SshAccessDto;
import io.github.connellite.proxy.client.rpc.dto.SshIssuedKeyDto;
import io.github.connellite.proxy.client.rpc.dto.SshUserKeyRowDto;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.Rpc;

public class SshAccessPage extends Composite {

    private final AppShell shell;
    private final String userId;
    private final FlowPanel tableHost = new FlowPanel();
    private final CheckBox passwordEnabled = Forms.checkbox("Allow SSH password authentication");
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
        root.add(new HTML("<h1>SSH</h1>"));
//        Label userLabel = new Label(userId == null ? "" : userId);
//        userLabel.setStyleName("hint");
//        root.add(userLabel);

        FlowPanel passwordPanel = new FlowPanel();
        passwordPanel.setStyleName("panel form");
        passwordPanel.add(passwordEnabled);
        Label passwordHint = new Label("Turns SSH password login on or off for this user. "
                + "HTTP, SOCKS, and admin login keep using the account password.");
        passwordHint.setStyleName("field-hint");
        passwordPanel.add(passwordHint);
        Button savePassword = new Button("Save");
        savePassword.setStyleName("primary");
        savePassword.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                savePassword();
            }
        });
        passwordPanel.add(Forms.formActions(savePassword));
        root.add(passwordPanel);

        root.add(new HTML("<h2>Keys</h2>"));
        Label keysHint = new Label("The server stores only the public key. "
                + "The private key is shown once, when you issue it.");
        keysHint.setStyleName("hint");
        root.add(keysHint);
        tableHost.setStyleName("table-wrap");
        root.add(tableHost);

        FlowPanel issuePanel = new FlowPanel();
        issuePanel.setStyleName("panel form");
        issuePanel.add(new HTML("<h2>Issue key</h2>"));
        issuePanel.add(Forms.field("Comment", comment, "Optional label so you can tell keys apart."));
        issuePanel.add(Forms.field("Passphrase", passphrase,
                "Optional. Encrypts the downloaded private key file only. "
                        + "The server does not store it. Leave blank for an unencrypted key."));
        Button issue = new Button("Issue key");
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
        issuedPanel.add(new HTML("<h2>Private key</h2>"));
        issuedWarning.setStyleName("hint");
        issuedPanel.add(issuedWarning);
        issuedPanel.add(privateKey);
        Button download = new Button("Download");
        download.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                downloadPrivateKey();
            }
        });
        Button back = new Button("Back to users");
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
        shell.getRpc().getSshAccess(userId, new AsyncCallback<SshAccessDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(SshAccessDto result) {
                passwordEnabled.setValue(result.isSshPasswordEnabled());
                renderKeys(result);
            }
        });
    }

    private void renderKeys(SshAccessDto page) {
        tableHost.clear();
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        String[] headers = {"Comment", "Fingerprint", "Created", "Actions"};
        for (int i = 0; i < headers.length; i++) {
            table.setText(0, i, headers[i]);
            styleHeaderCell(table, 0, i);
        }

        int row = 1;
        if (page.getKeys() != null) {
            for (final SshUserKeyRowDto key : page.getKeys()) {
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
            table.setText(1, 0, "No keys yet.");
            table.getFlexCellFormatter().setColSpan(1, 0, 4);
            table.getCellFormatter().setStyleName(1, 0, "empty");
        }
        tableHost.add(table);
    }

    private FlowPanel revokeButton(final SshUserKeyRowDto key) {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("actions");
        Button revoke = new Button("Revoke");
        revoke.setStyleName("danger");
        revoke.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                String label = key.getFingerprint() == null ? "this key" : key.getFingerprint();
                if (Window.confirm("Revoke " + label + "? Clients using it will no longer be able to connect.")) {
                    shell.getRpc().revokeSshKey(userId, key.getId(), new AsyncCallback<Void>() {
                        @Override
                        public void onFailure(Throwable caught) {
                            Rpc.showFailure(caught);
                        }

                        @Override
                        public void onSuccess(Void result) {
                            shell.showFlash("Key revoked", true);
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
                shell.showFlash(enabled ? "SSH password enabled" : "SSH password disabled", true);
            }
        });
    }

    private void issueKey() {
        final String commentText = comment.getText();
        final String passphraseText = passphrase.getText();
        shell.getRpc().issueSshKey(userId, commentText, passphraseText, new AsyncCallback<SshIssuedKeyDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(SshIssuedKeyDto result) {
                comment.setText("");
                passphrase.setText("");
                showIssued(result);
                shell.showFlash("Key issued. Save the private key now.", true);
                load();
            }
        });
    }

    private void showIssued(SshIssuedKeyDto issued) {
        StringBuilder warning = new StringBuilder();
        warning.append("This private key is shown once. Save it now. The server does not keep a copy.");
        if (issued.isEncrypted()) {
            warning.append(" This file is encrypted. OpenSSH or PuTTY will ask for the passphrase locally. "
                    + "The server does not store it.");
        }
        if (issued.getFingerprint() != null && !issued.getFingerprint().isEmpty()) {
            warning.append(" Fingerprint: ").append(issued.getFingerprint()).append(".");
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
        return value == null || value.isEmpty() ? "—" : value;
    }

    private static void styleHeaderCell(FlexTable table, int row, int col) {
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("background", "#efe8dc");
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("fontSize", "0.75rem");
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("textTransform", "uppercase");
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("fontWeight", "700");
        table.getCellFormatter().getElement(row, col).getStyle().setProperty("color", "#6b645a");
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
