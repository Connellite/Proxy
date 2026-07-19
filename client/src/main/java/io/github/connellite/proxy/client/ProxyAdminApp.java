package io.github.connellite.proxy.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;
import io.github.connellite.proxy.client.rpc.AdminService;
import io.github.connellite.proxy.client.rpc.AdminServiceAsync;
import io.github.connellite.proxy.client.ui.AppShell;

public class ProxyAdminApp implements EntryPoint {

    @Override
    public void onModuleLoad() {
        AdminServiceAsync rpc = GWT.create(AdminService.class);
        AppShell shell = new AppShell(rpc);
        RootPanel.get().add(shell);
        shell.showDashboard();
    }
}
