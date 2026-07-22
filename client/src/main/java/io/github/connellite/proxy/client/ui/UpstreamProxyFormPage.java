package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormDto;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

public class UpstreamProxyFormPage extends Composite {

    private final AppShell shell;
    private final Long editId;
    private boolean creating = true;
    private boolean passwordSaved;

    private final HTML title = new HTML("<h1>Upstream</h1>");
    private final TextBox name = new TextBox();
    private final ListBox type = new ListBox();
    private final TextBox host = new TextBox();
    private final PlainIntegerBox port = new PlainIntegerBox();
    private final TextBox username = new TextBox();
    private final PasswordTextBox password = new PasswordTextBox();
    private final Label passwordHint = new Label();
    private final Label sshHint = new Label();

    public UpstreamProxyFormPage(AppShell shell, Long id) {
        this.shell = shell;
        this.editId = id;

        type.addItem("HTTP", "HTTP");
        type.addItem("SOCKS5", "SOCKS5");
        type.addItem("SSH", "SSH");
        name.getElement().setAttribute("maxlength", "128");
        host.getElement().setAttribute("maxlength", "255");
        username.getElement().setAttribute("maxlength", "128");
        password.getElement().setAttribute("maxlength", "256");
        port.setMin(1);
        port.setMax(65535);
        type.addChangeHandler(new ChangeHandler() {
            @Override
            public void onChange(ChangeEvent event) {
                onTypeChanged(true);
            }
        });

        FlowPanel root = new FlowPanel();
        root.add(title);

        FlowPanel panel = new FlowPanel();
        panel.setStyleName("panel form");
        panel.add(Forms.field("Name", name));
        panel.add(Forms.field("Type", type));
        panel.add(Forms.field("Host", host));
        panel.add(Forms.field("Port", port));
        panel.add(Forms.field("Username", username));
        panel.add(Forms.field("Password", password));
        passwordHint.setStyleName("field-hint");
        panel.add(passwordHint);
        sshHint.setStyleName("muted");
        sshHint.setText("SSH upstream uses password auth and TCP port forwarding "
                + "(same as connecting with ssh -L). Username is required.");
        panel.add(sshHint);

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
                shell.showUpstreamProxies();
            }
        });
        panel.add(Forms.formActions(save, cancel));

        root.add(panel);
        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getUpstreamProxyForm(editId, new AsyncCallback<UpstreamProxyFormDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(UpstreamProxyFormDto form) {
                creating = form.isCreating() || form.getId() == null;
                passwordSaved = form.isPasswordSaved();
                title.setHTML(creating ? "<h1>New upstream</h1>" : "<h1>Edit upstream</h1>");
                name.setText(nullToEmpty(form.getName()));
                selectType(form.getType());
                host.setText(nullToEmpty(form.getHost()));
                int defaultPort = "SSH".equalsIgnoreCase(form.getType()) ? 22 : 8080;
                port.setIntValue(form.getPort() > 0 ? form.getPort() : defaultPort);
                username.setText(nullToEmpty(form.getUsername()));
                password.setText("");
                onTypeChanged(false);
            }
        });
    }

    private void save() {
        UpstreamProxyFormDto form = new UpstreamProxyFormDto();
        form.setId(editId);
        form.setCreating(creating);
        form.setName(name.getText().trim());
        form.setType(type.getSelectedValue());
        form.setHost(host.getText().trim());
        Integer portValue = port.getIntValue();
        form.setPort(portValue == null ? 0 : portValue);
        form.setUsername(username.getText().trim());
        form.setPassword(password.getText());

        AsyncCallback<Void> callback = new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showUpstreamProxies();
                shell.showFlash(creating ? "Upstream created" : "Upstream updated", true);
            }
        };

        if (creating) {
            shell.getRpc().createUpstreamProxy(form, callback);
        } else {
            shell.getRpc().updateUpstreamProxy(form, callback);
        }
    }

    private void onTypeChanged(boolean suggestDefaultPort) {
        boolean ssh = "SSH".equalsIgnoreCase(type.getSelectedValue());
        sshHint.setVisible(ssh);
        if (ssh) {
            passwordHint.setText(passwordSaved
                    ? "Password is saved. Leave blank to keep the current value."
                    : "Username is required for SSH. Password is sent to the upstream SSH server.");
            if (suggestDefaultPort) {
                Integer current = port.getIntValue();
                if (current == null || current == 8080 || current == 1080 || current == 3128) {
                    port.setIntValue(22);
                }
            }
        } else {
            passwordHint.setText(passwordSaved
                    ? "Password is saved. Leave blank to keep the current value."
                    : "Leave username empty for no authentication.");
            if (suggestDefaultPort) {
                Integer current = port.getIntValue();
                if (current == null || current == 22) {
                    port.setIntValue("SOCKS5".equalsIgnoreCase(type.getSelectedValue()) ? 1080 : 8080);
                }
            }
        }
    }

    private void selectType(String value) {
        String normalized = value == null ? "HTTP" : value.trim().toUpperCase();
        for (int i = 0; i < type.getItemCount(); i++) {
            if (type.getValue(i).equalsIgnoreCase(normalized)) {
                type.setSelectedIndex(i);
                return;
            }
        }
        type.setSelectedIndex(0);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
