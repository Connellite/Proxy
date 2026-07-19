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
import io.github.connellite.proxy.client.rpc.dto.UserFormDto;
import io.github.connellite.proxy.client.util.DateInput;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

public class UserFormPage extends Composite {

    private final AppShell shell;
    private final Long editId;
    private boolean creating = true;

    private final HTML title = new HTML("<h1>User</h1>");
    private final TextBox username = new TextBox();
    private final PasswordTextBox password = new PasswordTextBox();
    private final CheckBox enabled = Forms.checkbox("Enabled");
    private final PlainIntegerBox maxConnections = new PlainIntegerBox();
    private final DateInput expiresAt = new DateInput();

    public UserFormPage(AppShell shell, Long id) {
        this.shell = shell;
        this.editId = id;

        username.getElement().setAttribute("maxlength", "64");
        password.getElement().setAttribute("maxlength", "128");
        maxConnections.setMin(0);
        maxConnections.setMax(10000);

        FlowPanel root = new FlowPanel();
        root.add(title);

        FlowPanel panel = new FlowPanel();
        panel.setStyleName("panel form");
        panel.add(Forms.field("Username", username));
        panel.add(Forms.field(id == null ? "Password" : "Password (leave blank to keep)", password));
        panel.add(enabled);
        panel.add(Forms.field("Max connections (0 = unlimited)", maxConnections));
        panel.add(Forms.field("Expires on (optional)", expiresAt));

        Button save = new Button(id == null ? "Create" : "Save");
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                save();
            }
        });
        Button cancel = new Button("Cancel");
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

    private void load() {
        shell.getRpc().getUserForm(editId, new AsyncCallback<UserFormDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(UserFormDto form) {
                creating = form.isCreating() || form.getId() == null;
                title.setHTML(creating ? "<h1>New user</h1>" : "<h1>Edit user</h1>");
                username.setText(nullToEmpty(form.getUsername()));
                password.setText(nullToEmpty(form.getPassword()));
                expiresAt.setDateValue(nullToEmpty(form.getExpiresAt()));
                enabled.setValue(form.isEnabled());
                maxConnections.setIntValue(form.getMaxConnections());
                username.setEnabled(creating);
            }
        });
    }

    private void save() {
        UserFormDto form = new UserFormDto();
        form.setId(editId);
        form.setCreating(creating);
        form.setUsername(username.getText().trim());
        form.setPassword(password.getText());
        String expires = expiresAt.getDateValue();
        form.setExpiresAt(expires.isEmpty() ? null : expires);
        form.setEnabled(enabled.getValue());
        Integer max = maxConnections.getIntValue();
        form.setMaxConnections(max == null ? 0 : max);

        AsyncCallback<Void> callback = new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showUsers();
                shell.showFlash(creating ? "User created" : "User updated", true);
            }
        };

        if (creating) {
            shell.getRpc().createUser(form, callback);
        } else {
            shell.getRpc().updateUser(form, callback);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
