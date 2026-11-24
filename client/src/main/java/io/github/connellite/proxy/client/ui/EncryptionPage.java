package io.github.connellite.proxy.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.client.SafeHtmlTemplates.Template;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.EncryptionMessages;
import io.github.connellite.proxy.client.rpc.dto.EncryptionGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusGWTDTO;
import io.github.connellite.proxy.client.util.BindHostList;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

public class EncryptionPage extends Composite {

    private final AppShell shell;

    private final EncryptionMessages text = AppMessages.ENCRYPTION;
    private final CheckBox httpsEnabled = Forms.checkbox(text.enableHttps());
    private final CheckBox httpsAuthRequired = Forms.checkbox(text.httpsAuth());
    private final BindHostList httpsBindHost = new BindHostList();
    private final PlainIntegerBox httpsPort = new PlainIntegerBox();
    private final TextBox serverName = new TextBox();
    private final TextArea certificateChain = new TextArea();
    private final TextBox certificatePath = new TextBox();
    private final TextArea privateKey = new TextArea();
    private final TextBox privateKeyPath = new TextBox();
    private final Label keyHint = new Label();
    private final HTML runtime = new HTML();
    private final FlowPanel tlsPanel = new FlowPanel();
    private boolean privateKeySaved;
    private static final Templates TEMPLATES = GWT.create(Templates.class);

    interface Templates extends SafeHtmlTemplates {
        @Template("<p class=\"muted\">{0} <code>{1}</code></p>")
        SafeHtml labeledCode(String label, String value);

        @Template("<p class=\"muted\">{0}</p>")
        SafeHtml muted(String text);

        @Template("<p class=\"muted\"><code>{0}</code></p>")
        SafeHtml name(String value);
    }

    public EncryptionPage(AppShell shell) {
        this.shell = shell;
        httpsPort.setMin(1);
        httpsPort.setMax(65535);
        serverName.getElement().setAttribute("maxlength", "255");
        serverName.getElement().setAttribute("placeholder", text.serverNamePlaceholder());
        certificatePath.getElement().setAttribute("maxlength", "1024");
        certificatePath.getElement().setAttribute("placeholder", text.certificatePathPlaceholder());
        privateKeyPath.getElement().setAttribute("maxlength", "1024");
        privateKeyPath.getElement().setAttribute("placeholder", text.privateKeyPathPlaceholder());
        certificateChain.setVisibleLines(10);
        certificateChain.getElement().setAttribute("placeholder", text.certificatePlaceholder());
        privateKey.setVisibleLines(8);

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        header.add(new HTML(UiTemplates.HEADINGS.h1(text.title())));
        runtime.setStyleName("muted");
        runtime.getElement().getStyle().setProperty("margin", "0");
        header.add(runtime);
        root.add(header);

        FlowPanel form = new FlowPanel();
        form.setStyleName("panel form");
        form.add(httpsEnabled);
        form.add(Forms.twoCol(
                Forms.field(text.httpsBind(), httpsBindHost, AppMessages.COMMON.bindHint()),
                Forms.field(text.httpsPort(), httpsPort)));
        form.add(httpsAuthRequired);
        form.add(Forms.field(text.serverName(), serverName, text.serverNameHint()));
        form.add(Forms.field(text.certificates(), certificateChain, text.certificatesHint()));
        form.add(Forms.field(text.certificatePath(), certificatePath, text.certificatePathHint()));
        form.add(Forms.field(text.privateKey(), privateKey, text.privateKeyHint()));
        keyHint.setStyleName("field-hint");
        keyHint.setVisible(false);
        form.add(keyHint);
        form.add(Forms.field(text.privateKeyPath(), privateKeyPath, text.privateKeyPathHint()));

        tlsPanel.setStyleName("tls-status");
        tlsPanel.setVisible(false);
        form.add(tlsPanel);

        Button save = new Button(text.saveSettings());
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                save();
            }
        });
        Button back = new Button(text.backToSettings());
        back.setStyleName("button");
        back.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                shell.showSettings();
            }
        });
        form.add(Forms.formActions(save, back));
        root.add(form);
        initWidget(root);
        load();
    }

    private void load() {
        shell.getRpc().getEncryption(new AsyncCallback<EncryptionGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(EncryptionGWTDTO dto) {
                apply(dto);
            }
        });
    }

    private void apply(EncryptionGWTDTO dto) {
        privateKeySaved = dto.isPrivateKeySaved();
        httpsEnabled.setValue(dto.isHttpsEnabled());
        httpsAuthRequired.setValue(dto.isHttpsAuthRequired());
        httpsBindHost.fill(dto.getBindHostOptions(), dto.getHttpsBindHost());
        httpsPort.setIntValue(dto.getHttpsPort());
        serverName.setText(nullToEmpty(dto.getServerName()));
        certificateChain.setText(nullToEmpty(dto.getCertificateChain()));
        certificatePath.setText(nullToEmpty(dto.getCertificatePath()));
        privateKey.setText("");
        privateKey.setFocus(false);
        privateKey.getElement().setAttribute("placeholder", privateKeySaved
                ? text.privateKeySavedPlaceholder()
                : text.privateKeyEmptyPlaceholder());
        privateKeyPath.setText(nullToEmpty(dto.getPrivateKeyPath()));
        runtime.setText(text.httpsRuntime(dto.isHttpsRunning()
                ? AppMessages.COMMON.running() : AppMessages.COMMON.stopped()));
        keyHint.setText(privateKeySaved ? text.privateKeySavedHint() : "");
        keyHint.setVisible(privateKeySaved);
        if (dto.getLastError() != null && !dto.getLastError().isEmpty()) {
            shell.showFlash(dto.getLastError(), false);
        }
        renderTls(dto.getTlsStatus());
    }

    private void save() {
        EncryptionGWTDTO dto = collect();
        shell.getRpc().saveEncryption(dto, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash(text.saved(), true);
                load();
            }
        });
    }

    private EncryptionGWTDTO collect() {
        EncryptionGWTDTO dto = new EncryptionGWTDTO();
        dto.setHttpsEnabled(httpsEnabled.getValue());
        dto.setHttpsAuthRequired(httpsAuthRequired.getValue());
        dto.setHttpsBindHost(httpsBindHost.selected());
        Integer port = httpsPort.getIntValue();
        dto.setHttpsPort(port == null ? 0 : port);
        dto.setServerName(serverName.getText().trim());
        dto.setCertificateChain(certificateChain.getText());
        dto.setCertificatePath(certificatePath.getText().trim());
        dto.setPrivateKey(privateKey.getText());
        dto.setPrivateKeyPath(privateKeyPath.getText().trim());
        dto.setPrivateKeySaved(privateKeySaved);
        return dto;
    }

    private void renderTls(TlsStatusGWTDTO tls) {
        tlsPanel.clear();
        if (tls == null || !(tls.isValidCert() || tls.isValidKey()
                || (tls.getWarningValidation() != null && !tls.getWarningValidation().isEmpty()))) {
            tlsPanel.setVisible(false);
            return;
        }
        tlsPanel.setVisible(true);
        tlsPanel.add(new HTML(UiTemplates.HEADINGS.h3(text.certificateStatus())));

        FlowPanel flags = new FlowPanel();
        flags.setStyleName("tls-flags");
        String valid = text.valid();
        String invalid = text.invalid();
        flags.add(flag(text.certificateFlag(tls.isValidCert() ? valid : invalid), tls.isValidCert()));
        String keyState = tls.isValidKey() ? valid : invalid;
        String keyLabel = tls.getKeyType() != null && !tls.getKeyType().isEmpty()
                ? text.privateKeyFlagTyped(keyState, tls.getKeyType())
                : text.privateKeyFlag(keyState);
        flags.add(flag(keyLabel, tls.isValidKey()));
        flags.add(flag(text.pairFlag(tls.isValidPair() ? valid : invalid), tls.isValidPair()));
        flags.add(flag(text.chainFlag(tls.isValidChain() ? valid : invalid),
                tls.isValidChain(), !tls.isValidChain()));
        tlsPanel.add(flags);

        if (tls.getSubject() != null && !tls.getSubject().isEmpty()) {
            tlsPanel.add(new HTML(TEMPLATES.labeledCode(text.subject(), tls.getSubject())));
        }
        if (tls.getIssuer() != null && !tls.getIssuer().isEmpty()) {
            tlsPanel.add(new HTML(TEMPLATES.labeledCode(text.issuer(), tls.getIssuer())));
        }
        if (tls.getNotBefore() != null && tls.getNotAfter() != null) {
            tlsPanel.add(new HTML(TEMPLATES.muted(text.validity(tls.getNotBefore(), tls.getNotAfter()))));
        }
        if (tls.getDnsNames() != null && !tls.getDnsNames().isEmpty()) {
            tlsPanel.add(new Label(text.names()));
            for (String dnsName : tls.getDnsNames()) {
                tlsPanel.add(new HTML(TEMPLATES.name(dnsName)));
            }
        }
        if (tls.getWarningValidation() != null && !tls.getWarningValidation().isEmpty()) {
            Label warn = new Label(tls.getWarningValidation());
            warn.setStyleName("flash warn");
            tlsPanel.add(warn);
        }
    }

    private static Label flag(String text, boolean ok) {
        return flag(text, ok, false);
    }

    private static Label flag(String text, boolean ok, boolean warn) {
        Label label = new Label(text);
        label.setStyleName(ok ? "ok" : (warn ? "warn" : "bad"));
        return label;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
