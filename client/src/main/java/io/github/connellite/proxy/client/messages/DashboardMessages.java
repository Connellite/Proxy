package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;
import com.google.gwt.safehtml.shared.SafeHtml;

@DefaultLocale("en")
public interface DashboardMessages extends Messages {

    @Key("title")
    String title();

    @Key("tabProxy")
    String tabProxy();

    @Key("tabDns")
    String tabDns();

    @Key("sectionZeroOmega")
    String sectionZeroOmega();

    @Key("sectionDnsRelay")
    String sectionDnsRelay();

    @Key("statHttpProxy")
    String statHttpProxy();

    @Key("statHttpsProxy")
    String statHttpsProxy();

    @Key("statSocksProxy")
    String statSocksProxy();

    @Key("statSshProxy")
    String statSshProxy();

    @Key("statActiveChannels")
    String statActiveChannels();

    @Key("statUsers")
    String statUsers();

    @Key("statTrafficUpSession")
    String statTrafficUpSession();

    @Key("statTrafficDownSession")
    String statTrafficDownSession();

    @Key("statTrafficUpAll")
    String statTrafficUpAll();

    @Key("statTrafficDownAll")
    String statTrafficDownAll();

    @Key("statDns")
    String statDns();

    @Key("statTcpRelay")
    String statTcpRelay();

    @Key("statDoh")
    String statDoh();

    @Key("statDoh3")
    String statDoh3();

    @Key("statDot")
    String statDot();

    @Key("statDoq")
    String statDoq();

    @Key("statRoutes")
    String statRoutes();

    @Key("statAdvertised")
    String statAdvertised();

    @Key("statUpstreamResolver")
    String statUpstreamResolver();

    @Key("statTcpViaUpstream")
    String statTcpViaUpstream();

    @Key("zeroOmegaHttp")
    SafeHtml zeroOmegaHttp(int port);

    @Key("zeroOmegaHttps")
    SafeHtml zeroOmegaHttps(int port);

    @Key("zeroOmegaSocks")
    SafeHtml zeroOmegaSocks(int port);

    @Key("zeroOmegaSsh")
    SafeHtml zeroOmegaSsh(int port);

    @Key("connectionStrings")
    String connectionStrings();

    @Key("plainDns")
    String plainDns();

    @Key("emptyDnsListeners")
    String emptyDnsListeners();
}
