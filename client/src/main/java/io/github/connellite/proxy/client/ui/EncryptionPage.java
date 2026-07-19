package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import io.github.connellite.proxy.client.rpc.dto.EncryptionDto;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusDto;
import io.github.connellite.proxy.client.util.Forms;
import io.github.connellite.proxy.client.util.PlainIntegerBox;
import io.github.connellite.proxy.client.util.Rpc;

public class EncryptionPage extends Composite {

    private final AppShell shell;

    private final CheckBox httpsEnabled = Forms.checkbox("Enable HTTPS proxy encryption");
    private final TextBox httpsBindHost = new TextBox();
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

    public EncryptionPage(AppShell shell) {
        this.shell = shell;
        httpsPort.setMin(1);
        httpsPort.setMax(65535);
        serverName.getElement().setAttribute("maxlength", "255");
        serverName.getElement().setAttribute("placeholder", "proxy.example.com");
        certificatePath.getElement().setAttribute("maxlength", "1024");
        certificatePath.getElement().setAttribute("placeholder",
                "/etc/letsencrypt/live/proxy.example.com/fullchain.pem");
        privateKeyPath.getElement().setAttribute("maxlength", "1024");
        privateKeyPath.getElement().setAttribute("placeholder",
                "/etc/letsencrypt/live/proxy.example.com/privkey.pem");
        certificateChain.setVisibleLines(10);
        certificateChain.getElement().setAttribute("placeholder",
                "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----");
        privateKey.setVisibleLines(8);

        FlowPanel root = new FlowPanel();
        FlowPanel header = new FlowPanel();
        header.setStyleName("row-between");
        header.add(new HTML("<h1>Encryption settings</h1>"));
        runtime.setStyleName("muted");
        runtime.getElement().getStyle().setProperty("margin", "0");
        header.add(runtime);
        root.add(header);

        FlowPanel form = new FlowPanel();
        form.setStyleName("panel form");
        form.add(httpsEnabled);
        form.add(Forms.twoCol(
                Forms.field("HTTPS bind host", httpsBindHost),
                Forms.field("HTTPS port", httpsPort)));
        form.add(Forms.field("Server name", serverName,
                "Must match a DNS name or IP in the certificate."));
        form.add(Forms.field("Certificates", certificateChain,
                "Paste the PEM certificate chain (for example Let’s Encrypt fullchain.pem). "
                        + "Do not set both paste and path."));
        form.add(Forms.field("Certificate path", certificatePath,
                "Or path to a PEM certificate chain file on this server."));
        form.add(Forms.field("Private key", privateKey,
                "Paste the PEM private key. Do not set both paste and path."));
        keyHint.setStyleName("field-hint");
        keyHint.setVisible(false);
        form.add(keyHint);
        form.add(Forms.field("Private key path", privateKeyPath,
                "Or path to a PEM private key file on this server."));

        tlsPanel.setStyleName("tls-status");
        tlsPanel.setVisible(false);
        form.add(tlsPanel);

        Button save = new Button("Save settings");
        save.setStyleName("primary");
        save.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                save();
            }
        });
        Button back = new Button("Back to settings");
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
        shell.getRpc().getEncryption(new AsyncCallback<EncryptionDto>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(EncryptionDto dto) {
                apply(dto);
            }
        });
    }

    private void apply(EncryptionDto dto) {
        privateKeySaved = dto.isPrivateKeySaved();
        httpsEnabled.setValue(dto.isHttpsEnabled());
        httpsBindHost.setText(nullToEmpty(dto.getHttpsBindHost()));
        httpsPort.setIntValue(dto.getHttpsPort());
        serverName.setText(nullToEmpty(dto.getServerName()));
        certificateChain.setText(nullToEmpty(dto.getCertificateChain()));
        certificatePath.setText(nullToEmpty(dto.getCertificatePath()));
        privateKey.setText("");
        privateKey.setFocus(false);
        privateKey.getElement().setAttribute("placeholder", privateKeySaved
                ? "Private key is saved. Leave empty to keep it, or paste a new key."
                : "-----BEGIN PRIVATE KEY-----");
        privateKeyPath.setText(nullToEmpty(dto.getPrivateKeyPath()));
        runtime.setHTML("HTTPS: " + (dto.isHttpsRunning() ? "running" : "stopped"));
        keyHint.setText(privateKeySaved
                ? "A private key is already saved and will not be shown again."
                : "");
        keyHint.setVisible(privateKeySaved);
        if (dto.getLastError() != null && !dto.getLastError().isEmpty()) {
            shell.showFlash(dto.getLastError(), false);
        }
        renderTls(dto.getTlsStatus());
    }

    private void save() {
        EncryptionDto dto = collect();
        shell.getRpc().saveEncryption(dto, new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable caught) {
                Rpc.showFailure(caught);
            }

            @Override
            public void onSuccess(Void result) {
                shell.showFlash("Encryption settings saved and listeners restarted", true);
                load();
            }
        });
    }

    private EncryptionDto collect() {
        EncryptionDto dto = new EncryptionDto();
        dto.setHttpsEnabled(httpsEnabled.getValue());
        dto.setHttpsBindHost(httpsBindHost.getText().trim());
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

    private void renderTls(TlsStatusDto tls) {
        tlsPanel.clear();
        if (tls == null || !(tls.isValidCert() || tls.isValidKey()
                || (tls.getWarningValidation() != null && !tls.getWarningValidation().isEmpty()))) {
            tlsPanel.setVisible(false);
            return;
        }
        tlsPanel.setVisible(true);
        tlsPanel.add(new HTML("<h3>Certificate status</h3>"));

        FlowPanel flags = new FlowPanel();
        flags.setStyleName("tls-flags");
        flags.add(flag("Certificate: " + (tls.isValidCert() ? "valid" : "invalid"), tls.isValidCert()));
        String keyLabel = "Private key: " + (tls.isValidKey() ? "valid" : "invalid");
        if (tls.getKeyType() != null && !tls.getKeyType().isEmpty()) {
            keyLabel += " (" + tls.getKeyType() + ")";
        }
        flags.add(flag(keyLabel, tls.isValidKey()));
        flags.add(flag("Pair: " + (tls.isValidPair() ? "valid" : "invalid"), tls.isValidPair()));
        flags.add(flag("Chain (system trust): " + (tls.isValidChain() ? "trusted" : "not trusted"),
                tls.isValidChain(), !tls.isValidChain()));
        tlsPanel.add(flags);

        if (tls.getSubject() != null && !tls.getSubject().isEmpty()) {
            tlsPanel.add(mutedHtml("Subject: <code>" + escape(tls.getSubject()) + "</code>"));
        }
        if (tls.getIssuer() != null && !tls.getIssuer().isEmpty()) {
            tlsPanel.add(mutedHtml("Issuer: <code>" + escape(tls.getIssuer()) + "</code>"));
        }
        if (tls.getNotBefore() != null && tls.getNotAfter() != null) {
            tlsPanel.add(mutedHtml("Validity: " + escape(tls.getNotBefore())
                    + " → " + escape(tls.getNotAfter())));
        }
        if (tls.getDnsNames() != null && !tls.getDnsNames().isEmpty()) {
            StringBuilder names = new StringBuilder("Names: ");
            for (int i = 0; i < tls.getDnsNames().size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append("<code>").append(escape(tls.getDnsNames().get(i))).append("</code>");
            }
            tlsPanel.add(mutedHtml(names.toString()));
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

    private static HTML mutedHtml(String html) {
        HTML el = new HTML("<p class=\"muted\">" + html + "</p>");
        return el;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
