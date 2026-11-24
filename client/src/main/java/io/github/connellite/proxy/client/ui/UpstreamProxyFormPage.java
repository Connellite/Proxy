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
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.UpstreamMessages;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyTypeGWT;
import io.github.connellite.proxy.client.util.EnumListBox;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

public class UpstreamProxyFormPage extends Composite {

    private final AppShell shell;
    private final Long editId;
    private boolean creating = true;
    private boolean passwordSaved;

    private final UpstreamMessages text = AppMessages.UPSTREAM;
    private final HTML title = new HTML(UiTemplates.HEADINGS.h1(text.title()));
    private final TextBox name = new TextBox();
    private final EnumListBox<UpstreamProxyTypeGWT> type = new EnumListBox<>(UpstreamProxyTypeGWT.values(),
            new EnumListBox.Labels<UpstreamProxyTypeGWT>() {
                @Override
                public String label(UpstreamProxyTypeGWT value) {
                    return AppMessages.upstreamType(value);
                }
            });
    private final TextBox host = new TextBox();
    private final PlainIntegerBox port = new PlainIntegerBox();
    private final TextBox username = new TextBox();
    private final PasswordTextBox password = new PasswordTextBox();
    private final Label passwordHint = new Label();
    private final Label sshHint = new Label();

    public UpstreamProxyFormPage(AppShell shell, Long id) {
        this.shell = shell;
        this.editId = id;

        type.select(UpstreamProxyTypeGWT.HTTP);
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
        panel.add(Forms.field(text.name(), name));
        panel.add(Forms.field(text.type(), type));
        panel.add(Forms.field(text.host(), host));
        panel.add(Forms.field(text.port(), port));
        panel.add(Forms.field(text.username(), username));
        panel.add(Forms.field(text.password(), password));
        passwordHint.setStyleName("field-hint");
        panel.add(passwordHint);
        sshHint.setStyleName("muted");
        sshHint.setText(text.sshHint());
        panel.add(sshHint);

        Button save = new Button(id == null ? AppMessages.COMMON.create() : AppMessages.COMMON.save());
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                save();
            }
        });
        Button cancel = new Button(AppMessages.COMMON.cancel());
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
        shell.getRpc().getUpstreamProxyForm(editId, new AsyncCallback<UpstreamProxyFormGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(UpstreamProxyFormGWTDTO form) {
                creating = form.isCreating() || form.getId() == null;
                passwordSaved = form.isPasswordSaved();
                title.setHTML(creating
                        ? UiTemplates.HEADINGS.h1(text.titleNew())
                        : UiTemplates.HEADINGS.h1(text.titleEdit()));
                name.setText(nullToEmpty(form.getName()));
                UpstreamProxyTypeGWT selectedType = form.getType() == null
                        ? UpstreamProxyTypeGWT.HTTP : form.getType();
                type.select(selectedType);
                host.setText(nullToEmpty(form.getHost()));
                port.setIntValue(form.getPort() > 0 ? form.getPort() : selectedType.defaultPort());
                username.setText(nullToEmpty(form.getUsername()));
                password.setText("");
                onTypeChanged(false);
            }
        });
    }

    private void save() {
        UpstreamProxyFormGWTDTO form = new UpstreamProxyFormGWTDTO();
        form.setId(editId);
        form.setCreating(creating);
        form.setName(name.getText().trim());
        form.setType(type.selected(UpstreamProxyTypeGWT.HTTP));
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
                shell.showFlash(creating ? text.created() : text.updated(), true);
            }
        };

        if (creating) {
            shell.getRpc().createUpstreamProxy(form, callback);
        } else {
            shell.getRpc().updateUpstreamProxy(form, callback);
        }
    }

    private void onTypeChanged(boolean suggestDefaultPort) {
        UpstreamProxyTypeGWT selected = type.selected(UpstreamProxyTypeGWT.HTTP);
        boolean ssh = selected == UpstreamProxyTypeGWT.SSH;
        sshHint.setVisible(ssh);
        if (ssh) {
            passwordHint.setText(passwordSaved ? text.passwordSaved() : text.sshPasswordEmpty());
        } else {
            passwordHint.setText(passwordSaved ? text.passwordSaved() : text.passwordEmpty());
        }
        if (suggestDefaultPort) {
            Integer current = port.getIntValue();
            if (current == null || current == 22 || current == 443 || current == 1080 || current == 8080 || current == 3128) {
                port.setIntValue(selected.defaultPort());
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
