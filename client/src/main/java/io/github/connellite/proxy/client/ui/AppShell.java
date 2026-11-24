package io.github.connellite.proxy.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.client.SafeHtmlTemplates.Template;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.ShellMessages;
import io.github.connellite.proxy.client.rpc.AdminServiceAsync;
import io.github.connellite.proxy.client.rpc.dto.SettingsGWTDTO;
import io.github.connellite.proxy.client.util.AutoRefresh;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class AppShell extends Composite {

    @Getter
    private final AdminServiceAsync rpc;
    private final SimplePanel content = new SimplePanel();
    private final Label flash = new Label();
    private Anchor sftpLink;
    /** Mirrors the saved "SSH enabled" setting; SFTP needs the SSH listener. */
    private boolean sshEnabled = true;
    private final List<AutoRefresh> autoRefresh = new ArrayList<>();
    private static final BrandTemplate BRAND = GWT.create(BrandTemplate.class);

    interface BrandTemplate extends SafeHtmlTemplates {
        @Template("<a href=\"#\">{0}</a>")
        SafeHtml link(String text);
    }

    public AppShell(AdminServiceAsync rpc) {
        this.rpc = rpc;

        FlowPanel root = new FlowPanel();
        root.setStyleName("app-shell");

        FlowPanel topbar = new FlowPanel();
        topbar.setStyleName("topbar");

        ShellMessages text = AppMessages.SHELL;
        HTMLPanel brand = new HTMLPanel("div", BRAND.link(text.brand()).asString());
        brand.setStyleName("brand");
        brand.addDomHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showDashboard();
            }
        }, ClickEvent.getType());

        HTMLPanel nav = new HTMLPanel("nav", "");
        nav.add(navLink(text.navDashboard(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showDashboard();
            }
        }));
        nav.add(navLink(text.navUsers(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showUsers();
            }
        }));
        nav.add(navLink(text.navSettings(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showSettings();
            }
        }));
        nav.add(navLink(text.navUpstream(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showUpstreamProxies();
            }
        }));
        nav.add(navLink(text.navStripHeaders(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showHttpStripHeaders();
            }
        }));
        nav.add(navLink(text.navEncryption(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showEncryption();
            }
        }));
        sftpLink = navLink(text.navSftp(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                if (sshEnabled) {
                    showSftp();
                } else {
                    showFlash(text.sftpRequiresSsh(), false);
                }
            }
        });
        nav.add(sftpLink);
        nav.add(navLink(text.navDnsRelay(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showRelay();
            }
        }));

        FlowPanel logout = new FlowPanel();
        logout.setStyleName("logout");
        Button logoutBtn = new Button(text.logout());
        logoutBtn.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                // Top-level navigation: FormPanel would submit into a hidden iframe,
                // and Spring's X-Frame-Options: DENY blocks the login redirect there.
                Window.Location.assign("logout");
            }
        });
        logout.add(logoutBtn);

        topbar.add(brand);
        topbar.add(nav);
        topbar.add(logout);

        FlowPanel container = new FlowPanel();
        container.setStyleName("container");
        flash.setVisible(false);
        container.add(flash);
        content.setStyleName("page-content");
        container.add(content);

        root.add(topbar);
        root.add(container);
        initWidget(root);
        setWidth("100%");
        refreshNavState();
    }

    /** Re-reads settings and greys out the SFTP section while the SSH proxy is disabled. */
    public void refreshNavState() {
        rpc.getSettings(new AsyncCallback<SettingsGWTDTO>() {
            @Override
            public void onFailure(Throwable caught) {
                // keep the current state; pages report their own RPC errors
            }

            @Override
            public void onSuccess(SettingsGWTDTO result) {
                sshEnabled = result.isSshEnabled();
                if (sshEnabled) {
                    sftpLink.removeStyleName("nav-disabled");
                    sftpLink.setTitle("");
                } else {
                    sftpLink.addStyleName("nav-disabled");
                    sftpLink.setTitle(AppMessages.SHELL.sftpRequiresSshTitle());
                }
            }
        });
    }

    public void showFlash(String message, boolean ok) {
        flash.setText(message == null ? "" : message);
        flash.setStyleName(ok ? "flash ok" : "flash err");
        flash.setVisible(message != null && !message.isEmpty());
    }

    public void clearFlash() {
        flash.setText("");
        flash.setVisible(false);
    }

    public void showDashboard() {
        stopRefresh();
        content.setWidget(new DashboardPage(this));
    }

    public void showUsers() {
        stopRefresh();
        content.setWidget(new UsersPage(this));
    }

    public void showUserForm(String id) {
        stopRefresh();
        clearFlash();
        content.setWidget(new UserFormPage(this, id));
    }

    public void showSshAccess(String userId) {
        stopRefresh();
        clearFlash();
        content.setWidget(new SshAccessPage(this, userId));
    }

    public void showSftp() {
        stopRefresh();
        content.setWidget(new SftpPage(this));
    }

    public void showSettings() {
        stopRefresh();
        content.setWidget(new SettingsPage(this));
    }

    public void showUpstreamProxies() {
        stopRefresh();
        content.setWidget(new UpstreamProxiesPage(this));
    }

    public void showUpstreamForm(Long id) {
        stopRefresh();
        clearFlash();
        content.setWidget(new UpstreamProxyFormPage(this, id));
    }

    public void showHttpStripHeaders() {
        stopRefresh();
        content.setWidget(new HttpStripHeadersPage(this));
    }

    public void showEncryption() {
        stopRefresh();
        content.setWidget(new EncryptionPage(this));
    }

    public void showRelay() {
        stopRefresh();
        content.setWidget(new RelayPage(this));
    }

    void attachRefresh(AutoRefresh refresh) {
        autoRefresh.add(refresh);
    }

    private void stopRefresh() {
        for (AutoRefresh refresh : autoRefresh) {
            refresh.stop();
        }
        autoRefresh.clear();
    }

    private Anchor navLink(String text, ClickHandler handler) {
        Anchor link = new Anchor(text, "#");
        link.addClickHandler(handler);
        return link;
    }
}
