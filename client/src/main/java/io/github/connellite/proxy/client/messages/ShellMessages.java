package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface ShellMessages extends Messages {

    @Key("brand")
    String brand();

    @Key("navDashboard")
    String navDashboard();

    @Key("navUsers")
    String navUsers();

    @Key("navSettings")
    String navSettings();

    @Key("navUpstream")
    String navUpstream();

    @Key("navStripHeaders")
    String navStripHeaders();

    @Key("navEncryption")
    String navEncryption();

    @Key("navSftp")
    String navSftp();

    @Key("navDnsRelay")
    String navDnsRelay();

    @Key("logout")
    String logout();

    @Key("sftpRequiresSsh")
    String sftpRequiresSsh();

    @Key("sftpRequiresSshTitle")
    String sftpRequiresSshTitle();
}
