package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface SettingsMessages extends Messages {

    @Key("title")
    String title();

    @Key("restartListeners")
    String restartListeners();

    @Key("encryptionSettings")
    String encryptionSettings();

    @Key("listeners")
    String listeners();

    @Key("http")
    String http();

    @Key("enableHttp")
    String enableHttp();

    @Key("httpBind")
    String httpBind();

    @Key("httpPort")
    String httpPort();

    @Key("httpAuth")
    String httpAuth();

    @Key("socks")
    String socks();

    @Key("enableSocks")
    String enableSocks();

    @Key("socksBind")
    String socksBind();

    @Key("socksPort")
    String socksPort();

    @Key("socksAuth")
    String socksAuth();

    @Key("socksUdp")
    String socksUdp();

    @Key("socksUdpHint")
    String socksUdpHint();

    @Key("ssh")
    String ssh();

    @Key("enableSsh")
    String enableSsh();

    @Key("sshBind")
    String sshBind();

    @Key("sshPort")
    String sshPort();

    @Key("sshHint")
    String sshHint();

    @Key("allowedNetworks")
    String allowedNetworks();

    @Key("allowedNetworksHint")
    String allowedNetworksHint();

    @Key("network")
    String network();

    @Key("networkSaved")
    String networkSaved();

    @Key("networkDeleted")
    String networkDeleted();

    @Key("saveRestart")
    String saveRestart();

    @Key("adminPassword")
    String adminPassword();

    @Key("currentPassword")
    String currentPassword();

    @Key("newPassword")
    String newPassword();

    @Key("confirmPassword")
    String confirmPassword();

    @Key("changePassword")
    String changePassword();

    @Key("adminPortHint")
    String adminPortHint();

    @Key("listenerStatus")
    String listenerStatus(String http, String https, String socks, String ssh);

    @Key("saved")
    String saved();

    @Key("restarted")
    String restarted();

    @Key("passwordChanged")
    String passwordChanged();
}
