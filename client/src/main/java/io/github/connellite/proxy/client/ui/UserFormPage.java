package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.CommonMessages;
import io.github.connellite.proxy.client.messages.UsersMessages;
import io.github.connellite.proxy.client.rpc.dto.UserFormGWTDTO;
import io.github.connellite.proxy.client.util.DateInput;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.PlainLongBox;
import io.github.connellite.proxy.client.util.Rpc;

public class UserFormPage extends Composite {

    private final AppShell shell;
    private final String editId;
    private boolean creating = true;
    private boolean bootstrapAdmin;

    private final UsersMessages text = AppMessages.USERS;
    private final CommonMessages common = AppMessages.COMMON;
    private final HTML title = new HTML(UiTemplates.HEADINGS.h1(text.titleUser()));
    private final TextBox username = new TextBox();
    private final PasswordTextBox password = new PasswordTextBox();
    private final CheckBox enabled = Forms.checkbox(common.enabled());
    private final CheckBox roleAdmin = Forms.checkbox(text.adminUi());
    private final CheckBox roleUser = Forms.checkbox(text.userProxy());
    private final PlainIntegerBox maxConnections = new PlainIntegerBox();
    private final PlainLongBox trafficLimitBytes = new PlainLongBox();
    private final PlainLongBox speedLimitUpBps = new PlainLongBox();
    private final PlainLongBox speedLimitDownBps = new PlainLongBox();
    private final DateInput expiresAt = new DateInput();

    public UserFormPage(AppShell shell, String id) {
        this.shell = shell;
        this.editId = id;

        username.getElement().setAttribute("maxlength", "64");
        password.getElement().setAttribute("maxlength", "128");
        maxConnections.setMin(0);
        maxConnections.setMax(10000);
        trafficLimitBytes.setMin(-1);
        speedLimitUpBps.setMin(-1);
        speedLimitDownBps.setMin(-1);

        FlowPanel root = new FlowPanel();
        root.add(title);

        FlowPanel panel = new FlowPanel();
        panel.setStyleName("panel form");
        panel.add(Forms.field(text.username(), username));
        panel.add(Forms.field(id == null ? text.password() : text.passwordKeep(), password));
        panel.add(enabled);
        panel.add(Forms.field(text.roles(), rolesPanel(), text.rolesFormHint()));
        panel.add(Forms.field(text.maxConnections(), maxConnections));
        panel.add(Forms.field(text.trafficLimit(), trafficLimitBytes, text.trafficLimitHint()));
        panel.add(Forms.twoCol(
                Forms.field(text.uploadLimit(), speedLimitUpBps, text.uploadHint()),
                Forms.field(text.downloadLimit(), speedLimitDownBps, text.downloadHint())));
        panel.add(Forms.field(text.expires(), expiresAt));

        Button save = new Button(id == null ? common.create() : common.save());
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                save();
            }
        });
        Button cancel = new Button(common.cancel());
        cancel.setStyleName("button");
        cancel.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showUsers();
            }
        });
        panel.add(Forms.formActions(save, cancel));

        root.add(panel);
        initWidget(root);
        load();
    }

    private FlowPanel rolesPanel() {
        FlowPanel roles = new FlowPanel();
        roles.add(roleAdmin);
        roles.add(roleUser);
        return roles;
    }

    private void load() {
        shell.getRpc().getUserForm(editId, new AsyncCallback<UserFormGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(UserFormGWTDTO form) {
                creating = form.isCreating() || form.getId() == null;
                bootstrapAdmin = form.isBootstrapAdmin();
                title.setHTML(creating ? UiTemplates.HEADINGS.h1(text.titleNew()) : UiTemplates.HEADINGS.h1(text.titleEdit()));
                username.setText(nullToEmpty(form.getId()));
                password.setText(nullToEmpty(form.getPassword()));
                expiresAt.setDateValue(nullToEmpty(form.getExpiresAt()));
                enabled.setValue(form.isEnabled());
                roleAdmin.setValue(bootstrapAdmin || form.isRoleAdmin());
                roleAdmin.setEnabled(!bootstrapAdmin);
                roleUser.setValue(form.isRoleUser());
                maxConnections.setIntValue(form.getMaxConnections());
                trafficLimitBytes.setLongValue(form.getTrafficLimitBytes());
                speedLimitUpBps.setLongValue(form.getSpeedLimitUpBps());
                speedLimitDownBps.setLongValue(form.getSpeedLimitDownBps());
                username.setEnabled(creating);
            }
        });
    }

    private void save() {
        UserFormGWTDTO form = new UserFormGWTDTO();
        form.setId(creating ? username.getText().trim() : editId);
        form.setCreating(creating);
        form.setPassword(password.getText());
        String expires = expiresAt.getDateValue();
        form.setExpiresAt(expires.isEmpty() ? null : expires);
        form.setEnabled(Boolean.TRUE.equals(enabled.getValue()));
        form.setRoleAdmin(bootstrapAdmin || Boolean.TRUE.equals(roleAdmin.getValue()));
        form.setRoleUser(Boolean.TRUE.equals(roleUser.getValue()));
        Integer max = maxConnections.getIntValue();
        form.setMaxConnections(max == null ? 0 : max);
        form.setTrafficLimitBytes(readLimit(trafficLimitBytes));
        form.setSpeedLimitUpBps(readLimit(speedLimitUpBps));
        form.setSpeedLimitDownBps(readLimit(speedLimitDownBps));

        AsyncCallback<Void> callback = new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showUsers();
                shell.showFlash(creating ? text.userCreated() : text.userUpdated(), true);
            }
        };

        if (creating) {
            shell.getRpc().createUser(form, callback);
        } else {
            shell.getRpc().updateUser(form, callback);
        }
    }

    private static long readLimit(PlainLongBox box) {
        Long value = box.getLongValue();
        return value == null || value < 0 ? -1L : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
