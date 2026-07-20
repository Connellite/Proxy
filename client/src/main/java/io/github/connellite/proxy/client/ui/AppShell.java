package io.github.connellite.proxy.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import io.github.connellite.proxy.client.rpc.AdminServiceAsync;
import io.github.connellite.proxy.client.util.AutoRefresh;
import lombok.Getter;

public class AppShell extends Composite {

    @Getter
    private final AdminServiceAsync rpc;
    private final SimplePanel content = new SimplePanel();
    private final Label flash = new Label();
    private AutoRefresh autoRefresh;

    public AppShell(AdminServiceAsync rpc) {
        this.rpc = rpc;

        FlowPanel root = new FlowPanel();
        root.setStyleName("app-shell");

        FlowPanel topbar = new FlowPanel();
        topbar.setStyleName("topbar");

        HTMLPanel brand = new HTMLPanel("div", "<a href=\"#\">Proxy Admin</a>");
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
        nav.add(navLink("Dashboard", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showDashboard();
            }
        }));
        nav.add(navLink("Users", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showUsers();
            }
        }));
        nav.add(navLink("Settings", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showSettings();
            }
        }));
        nav.add(navLink("Upstream", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showUpstreamProxies();
            }
        }));
        nav.add(navLink("Encryption", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                event.preventDefault();
                clearFlash();
                showEncryption();
            }
        }));

        FlowPanel logout = new FlowPanel();
        logout.setStyleName("logout");
        Button logoutBtn = new Button("Logout");
        logoutBtn.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                // Top-level navigation: FormPanel would submit into a hidden iframe,
                // and Spring's X-Frame-Options: DENY blocks the login redirect there.
                Window.Location.assign("/logout");
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

    public void showUserForm(Long id) {
        stopRefresh();
        clearFlash();
        content.setWidget(new UserFormPage(this, id));
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

    public void showEncryption() {
        stopRefresh();
        content.setWidget(new EncryptionPage(this));
    }

    void attachRefresh(AutoRefresh refresh) {
        stopRefresh();
        this.autoRefresh = refresh;
    }

    private void stopRefresh() {
        if (autoRefresh != null) {
            autoRefresh.stop();
            autoRefresh = null;
        }
    }

    private Anchor navLink(String text, ClickHandler handler) {
        Anchor link = new Anchor(text, "#");
        link.addClickHandler(handler);
        return link;
    }
}
