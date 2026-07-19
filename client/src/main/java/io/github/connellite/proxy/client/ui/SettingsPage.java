package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeDto;
import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

public class SettingsPage extends Composite {

    private final AppShell shell;

    private final CheckBox httpEnabled = Forms.checkbox("Enable HTTP proxy");
    private final CheckBox socksEnabled = Forms.checkbox("Enable SOCKS4/5 proxy");
    private final CheckBox httpAuthRequired = Forms.checkbox("Require username/password for HTTP / HTTPS proxy");
    private final CheckBox socksAuthRequired = Forms.checkbox(
            "Require username/password for SOCKS5 (SOCKS4 disabled while on)");
    private final CheckBox socksUdpEnabled = Forms.checkbox("Full SOCKS5 with UDP (TCP CONNECT + UDP ASSOCIATE)");
    private final TextBox httpBindHost = new TextBox();
    private final TextBox socksBindHost = new TextBox();
    private final PlainIntegerBox httpPort = new PlainIntegerBox();
    private final PlainIntegerBox socksPort = new PlainIntegerBox();
    private final HTML status = new HTML();

    private final PasswordTextBox currentPassword = new PasswordTextBox();
    private final PasswordTextBox newPassword = new PasswordTextBox();
    private final PasswordTextBox confirmPassword = new PasswordTextBox();

    public SettingsPage(AppShell shell) {
        this.shell = shell;
        httpPort.setMin(1);
        httpPort.setMax(65535);
        socksPort.setMin(1);
        socksPort.setMax(65535);
        newPassword.getElement().setAttribute("minlength", "4");
        confirmPassword.getElement().setAttribute("minlength", "4");

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        header.add(new HTML("<h1>Settings</h1>"));
        Button restart = new Button("Restart listeners");
        restart.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                restartProxy();
            }
        });
        header.add(restart);
        root.add(header);

        FlowPanel statusRow = new FlowPanel();
        statusRow.setStyleName("muted");
        statusRow.getElement().getStyle().setProperty("marginBottom", "1rem");
        statusRow.add(status);
        Anchor encLink = new Anchor("Encryption settings", "#");
        encLink.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                shell.showEncryption();
            }
        });
        statusRow.add(new HTML(" · "));
        statusRow.add(encLink);
        root.add(statusRow);

        FlowPanel listeners = new FlowPanel();
        listeners.setStyleName("panel form");
        listeners.add(new HTML("<h2>Listeners</h2>"));
        listeners.add(httpEnabled);
        listeners.add(Forms.twoCol(
                Forms.field("HTTP bind host", httpBindHost),
                Forms.field("HTTP port", httpPort)));
        listeners.add(httpAuthRequired);
        listeners.add(socksEnabled);
        listeners.add(Forms.twoCol(
                Forms.field("SOCKS bind host", socksBindHost),
                Forms.field("SOCKS port", socksPort)));
        listeners.add(socksAuthRequired);
        listeners.add(socksUdpEnabled);
        Label udpHint = new Label("When off, SOCKS5 accepts only TCP CONNECT (current default). "
                + "UDP ASSOCIATE is needed for some apps (DNS-over-SOCKS, games, VoIP).");
        udpHint.setStyleName("muted");
        listeners.add(udpHint);

        Button save = new Button("Save & restart");
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveSettings();
            }
        });
        listeners.add(Forms.formActions(save));
        root.add(listeners);

        FlowPanel passwordPanel = new FlowPanel();
        passwordPanel.setStyleName("panel form");
        passwordPanel.add(new HTML("<h2>Admin password</h2>"));
        passwordPanel.add(Forms.field("Current password", currentPassword));
        passwordPanel.add(Forms.field("New password", newPassword));
        passwordPanel.add(Forms.field("Confirm new password", confirmPassword));
        Button changePwd = new Button("Change password");
        changePwd.setStyleName("primary");
        changePwd.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                changePassword();
            }
        });
        passwordPanel.add(Forms.formActions(changePwd));
        root.add(passwordPanel);

        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getSettings(new AsyncCallback<SettingsDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(SettingsDto dto) {
                httpEnabled.setValue(dto.isHttpEnabled());
                socksEnabled.setValue(dto.isSocksEnabled());
                httpAuthRequired.setValue(dto.isHttpAuthRequired());
                socksAuthRequired.setValue(dto.isSocksAuthRequired());
                socksUdpEnabled.setValue(dto.isSocksUdpEnabled());
                httpBindHost.setText(nullToEmpty(dto.getHttpBindHost()));
                socksBindHost.setText(nullToEmpty(dto.getSocksBindHost()));
                httpPort.setIntValue(dto.getHttpPort());
                socksPort.setIntValue(dto.getSocksPort());
                status.setHTML("HTTP: " + onOff(dto.isHttpRunning())
                        + " · HTTPS: " + onOff(dto.isHttpsRunning())
                        + " · SOCKS4/5: " + onOff(dto.isSocksRunning()));
                if (dto.getLastError() != null && !dto.getLastError().isEmpty()) {
                    shell.showFlash(dto.getLastError(), false);
                }
            }
        });
    }

    private void saveSettings() {
        shell.getRpc().saveSettings(collectSettings(), new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash("Settings saved and proxy listeners restarted", true);
                load();
            }
        });
    }

    private void restartProxy() {
        shell.getRpc().restartProxy(new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash("Proxy listeners restarted", true);
                load();
            }
        });
    }

    private void changePassword() {
        PasswordChangeDto dto = new PasswordChangeDto();
        dto.setCurrentPassword(currentPassword.getText());
        dto.setNewPassword(newPassword.getText());
        dto.setConfirmPassword(confirmPassword.getText());
        shell.getRpc().changePassword(dto, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                currentPassword.setText("");
                newPassword.setText("");
                confirmPassword.setText("");
                shell.showFlash("Admin password changed", true);
            }
        });
    }

    private SettingsDto collectSettings() {
        SettingsDto dto = new SettingsDto();
        dto.setHttpEnabled(httpEnabled.getValue());
        dto.setSocksEnabled(socksEnabled.getValue());
        dto.setHttpAuthRequired(httpAuthRequired.getValue());
        dto.setSocksAuthRequired(socksAuthRequired.getValue());
        dto.setSocksUdpEnabled(socksUdpEnabled.getValue());
        dto.setHttpBindHost(httpBindHost.getText().trim());
        dto.setSocksBindHost(socksBindHost.getText().trim());
        Integer hp = httpPort.getIntValue();
        Integer sp = socksPort.getIntValue();
        dto.setHttpPort(hp == null ? 0 : hp);
        dto.setSocksPort(sp == null ? 0 : sp);
        return dto;
    }

    private static String onOff(boolean running) {
        return running ? "running" : "stopped";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
