package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.SettingsMessages;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayAllowRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SettingsGWTDTO;
import io.github.connellite.proxy.client.util.BindHostList;
import io.github.connellite.proxy.client.util.CellCopy;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

import java.util.List;

public class SettingsPage extends Composite {

    private final AppShell shell;

    private final SettingsMessages text = AppMessages.SETTINGS;
    private final CheckBox httpEnabled = Forms.checkbox(text.enableHttp());
    private final CheckBox socksEnabled = Forms.checkbox(text.enableSocks());
    private final CheckBox sshEnabled = Forms.checkbox(text.enableSsh());
    private final CheckBox httpAuthRequired = Forms.checkbox(text.httpAuth());
    private final CheckBox socksAuthRequired = Forms.checkbox(text.socksAuth());
    private final CheckBox socksUdpEnabled = Forms.checkbox(text.socksUdp());
    private final BindHostList httpBindHost = new BindHostList();
    private final BindHostList socksBindHost = new BindHostList();
    private final BindHostList sshBindHost = new BindHostList();
    private final PlainIntegerBox httpPort = new PlainIntegerBox();
    private final PlainIntegerBox socksPort = new PlainIntegerBox();
    private final PlainIntegerBox sshPort = new PlainIntegerBox();
    private final PlainIntegerBox outboundTtl = new PlainIntegerBox();
    private final TextBox allowInput = new TextBox();
    private final FlowPanel allowHost = new FlowPanel();
    private final Label adminPortHint = new Label();
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
        sshPort.setMin(1);
        sshPort.setMax(65535);
        outboundTtl.setMin(0);
        outboundTtl.setMax(255);
        newPassword.getElement().setAttribute("minlength", "4");
        confirmPassword.getElement().setAttribute("minlength", "4");

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        header.add(new HTML(UiTemplates.HEADINGS.h1(text.title())));
        Button restart = new Button(text.restartListeners());
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
        Anchor encLink = new Anchor(text.encryptionSettings(), "#");
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

        root.add(new HTML(UiTemplates.HEADINGS.h2(text.listeners())));

        FlowPanel httpPanel = new FlowPanel();
        httpPanel.setStyleName("panel form");
        httpPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.http())));
        httpPanel.add(httpEnabled);
        httpPanel.add(Forms.twoCol(
                Forms.field(text.httpBind(), httpBindHost, AppMessages.COMMON.bindHint()),
                Forms.field(text.httpPort(), httpPort)));
        httpPanel.add(httpAuthRequired);
        root.add(httpPanel);

        FlowPanel socksPanel = new FlowPanel();
        socksPanel.setStyleName("panel form");
        socksPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.socks())));
        socksPanel.add(socksEnabled);
        socksPanel.add(Forms.twoCol(
                Forms.field(text.socksBind(), socksBindHost, AppMessages.COMMON.bindHint()),
                Forms.field(text.socksPort(), socksPort)));
        socksPanel.add(socksAuthRequired);
        socksPanel.add(socksUdpEnabled);
        Label udpHint = new Label(text.socksUdpHint());
        udpHint.setStyleName("muted");
        socksPanel.add(udpHint);
        root.add(socksPanel);

        FlowPanel sshPanel = new FlowPanel();
        sshPanel.setStyleName("panel form");
        sshPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.ssh())));
        sshPanel.add(sshEnabled);
        sshPanel.add(Forms.twoCol(
                Forms.field(text.sshBind(), sshBindHost, AppMessages.COMMON.bindHint()),
                Forms.field(text.sshPort(), sshPort)));
        Label sshHint = new Label(text.sshHint());
        sshHint.setStyleName("muted");
        sshPanel.add(sshHint);
        root.add(sshPanel);

        FlowPanel allowPanel = new FlowPanel();
        allowPanel.setStyleName("panel form");
        Label allowTitle = new Label(text.allowedNetworks());
        allowTitle.getElement().getStyle().setProperty("fontWeight", "600");
        allowPanel.add(allowTitle);
        Label allowHint = new Label(text.allowedNetworksHint());
        allowHint.setStyleName("field-hint");
        allowPanel.add(allowHint);
        allowInput.setWidth("100%");
        allowPanel.add(allowInput);
        Button addNetwork = new Button(AppMessages.COMMON.add());
        addNetwork.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveAllow();
            }
        });
        allowPanel.add(Forms.formActions(addNetwork));
        allowHost.setVisible(false);
        allowPanel.add(allowHost);
        root.add(allowPanel);

        FlowPanel outboundPanel = new FlowPanel();
        outboundPanel.setStyleName("panel form");
        outboundPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.outbound())));
        outboundPanel.add(Forms.field(text.outboundTtl(), outboundTtl, text.outboundTtlHint()));
        root.add(outboundPanel);

        Button save = new Button(text.saveRestart());
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                saveSettings();
            }
        });
        FlowPanel saveRow = Forms.formActions(save);
        saveRow.addStyleName("settings-save");
        root.add(saveRow);

        FlowPanel passwordPanel = new FlowPanel();
        passwordPanel.setStyleName("panel form");
        passwordPanel.add(new HTML(UiTemplates.HEADINGS.h2(text.adminPassword())));
        passwordPanel.add(Forms.field(text.currentPassword(), currentPassword));
        passwordPanel.add(Forms.field(text.newPassword(), newPassword));
        passwordPanel.add(Forms.field(text.confirmPassword(), confirmPassword));
        Button changePwd = new Button(text.changePassword());
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

    private void renderAllows(List<RelayAllowRowGWTDTO> rows) {
        allowHost.clear();
        if (rows == null || rows.isEmpty()) {
            allowHost.setStyleName("");
            allowHost.setVisible(false);
            return;
        }
        allowHost.setStyleName("table-wrap");
        allowHost.setVisible(true);
        FlexTable table = new FlexTable();
        table.setStyleName("users-table");
        table.setText(0, 0, text.network());
        table.setText(0, 1, "");
        styleHeader(table, 0);
        styleHeader(table, 1);
        int row = 1;
        for (RelayAllowRowGWTDTO allow : rows) {
            table.setText(row, 0, allow.getCidr() == null ? "" : allow.getCidr());
            Button remove = new Button(AppMessages.COMMON.delete());
            remove.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    deleteAllow(allow.getId());
                }
            });
            table.setWidget(row, 1, remove);
            row++;
        }
        CellCopy.install(table);
        allowHost.add(table);
    }

    private void saveAllow() {
        shell.getRpc().saveProxyAllow(allowInput.getText(), new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                allowInput.setText("");
                shell.showFlash(text.networkSaved(), true);
                load();
            }
        });
    }

    private void deleteAllow(Long id) {
        if (id == null) {
            return;
        }
        shell.getRpc().deleteProxyAllow(id, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash(text.networkDeleted(), true);
                load();
            }
        });
    }

    private static void styleHeader(FlexTable table, int column) {
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("background", "#efe8dc");
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("fontSize", "0.75rem");
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("textTransform", "uppercase");
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("fontWeight", "700");
        table.getCellFormatter().getElement(0, column).getStyle().setProperty("color", "#6b645a");
    }

    private void load() {
        shell.getRpc().getSettings(new AsyncCallback<SettingsGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(SettingsGWTDTO dto) {
                httpEnabled.setValue(dto.isHttpEnabled());
                socksEnabled.setValue(dto.isSocksEnabled());
                sshEnabled.setValue(dto.isSshEnabled());
                httpAuthRequired.setValue(dto.isHttpAuthRequired());
                socksAuthRequired.setValue(dto.isSocksAuthRequired());
                socksUdpEnabled.setValue(dto.isSocksUdpEnabled());
                httpBindHost.fill(dto.getBindHostOptions(), dto.getHttpBindHost());
                socksBindHost.fill(dto.getBindHostOptions(), dto.getSocksBindHost());
                sshBindHost.fill(dto.getBindHostOptions(), dto.getSshBindHost());
                httpPort.setIntValue(dto.getHttpPort());
                socksPort.setIntValue(dto.getSocksPort());
                sshPort.setIntValue(dto.getSshPort());
                outboundTtl.setIntValue(dto.getOutboundTtl());
                renderAllows(dto.getProxyAllows());
                adminPortHint.setText(text.adminPortHint());
                status.setText(text.listenerStatus(
                        onOff(dto.isHttpRunning()),
                        onOff(dto.isHttpsRunning()),
                        onOff(dto.isSocksRunning()),
                        onOff(dto.isSshRunning())));
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
                shell.showFlash(text.saved(), true);
                shell.refreshNavState();
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
                shell.showFlash(text.restarted(), true);
                load();
            }
        });
    }

    private void changePassword() {
        PasswordChangeGWTDTO dto = new PasswordChangeGWTDTO();
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
                shell.showFlash(text.passwordChanged(), true);
            }
        });
    }

    private SettingsGWTDTO collectSettings() {
        SettingsGWTDTO dto = new SettingsGWTDTO();
        dto.setHttpEnabled(httpEnabled.getValue());
        dto.setSocksEnabled(socksEnabled.getValue());
        dto.setSshEnabled(sshEnabled.getValue());
        dto.setHttpAuthRequired(httpAuthRequired.getValue());
        dto.setSocksAuthRequired(socksAuthRequired.getValue());
        dto.setSocksUdpEnabled(socksUdpEnabled.getValue());
        dto.setHttpBindHost(httpBindHost.selected());
        dto.setSocksBindHost(socksBindHost.selected());
        dto.setSshBindHost(sshBindHost.selected());
        Integer hp = httpPort.getIntValue();
        Integer sp = socksPort.getIntValue();
        Integer sshp = sshPort.getIntValue();
        Integer ttl = outboundTtl.getIntValue();
        dto.setHttpPort(hp == null ? 0 : hp);
        dto.setSocksPort(sp == null ? 0 : sp);
        dto.setSshPort(sshp == null ? 0 : sshp);
        dto.setOutboundTtl(ttl == null ? 0 : ttl);
        return dto;
    }

    private static String onOff(boolean running) {
        return running ? AppMessages.COMMON.running() : AppMessages.COMMON.stopped();
    }
}
