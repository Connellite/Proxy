package io.github.connellite.proxy.client.messages;

import com.google.gwt.core.client.GWT;
import io.github.connellite.proxy.client.rpc.dto.AutoRefreshIntervalGWT;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteModeGWT;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyTypeGWT;

public final class AppMessages {

    public static final CommonMessages COMMON = GWT.create(CommonMessages.class);
    public static final ShellMessages SHELL = GWT.create(ShellMessages.class);
    public static final DashboardMessages DASHBOARD = GWT.create(DashboardMessages.class);
    public static final UsersMessages USERS = GWT.create(UsersMessages.class);
    public static final SettingsMessages SETTINGS = GWT.create(SettingsMessages.class);
    public static final SshMessages SSH = GWT.create(SshMessages.class);
    public static final SftpMessages SFTP = GWT.create(SftpMessages.class);
    public static final EncryptionMessages ENCRYPTION = GWT.create(EncryptionMessages.class);
    public static final UpstreamMessages UPSTREAM = GWT.create(UpstreamMessages.class);
    public static final StripHeadersMessages STRIP = GWT.create(StripHeadersMessages.class);
    public static final RelayMessages RELAY = GWT.create(RelayMessages.class);

    private AppMessages() {
    }

    public static String upstreamType(UpstreamProxyTypeGWT type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case HTTP:
                return UPSTREAM.typeHttp();
            case HTTPS:
                return UPSTREAM.typeHttps();
            case SOCKS5:
                return UPSTREAM.typeSocks5();
            case SSH:
                return UPSTREAM.typeSsh();
            default:
                return "";
        }
    }

    public static String routeMode(RelayRouteModeGWT mode) {
        if (mode == null) {
            return RELAY.modeRelay();
        }
        switch (mode) {
            case ADDRESS:
                return RELAY.modeAddress();
            case RELAY:
            default:
                return RELAY.modeRelay();
        }
    }

    public static String refreshInterval(AutoRefreshIntervalGWT interval) {
        if (interval == null) {
            return RELAY.intervalOff();
        }
        switch (interval) {
            case S1:
                return RELAY.intervalS1();
            case S2:
                return RELAY.intervalS2();
            case S3:
                return RELAY.intervalS3();
            case S5:
                return RELAY.intervalS5();
            case S10:
                return RELAY.intervalS10();
            case S30:
                return RELAY.intervalS30();
            case OFF:
            default:
                return RELAY.intervalOff();
        }
    }
}
