package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface RelayMessages extends Messages {

    @Key("title")
    String title();

    @Key("hint")
    String hint();

    @Key("listeners")
    String listeners();

    @Key("thisServer")
    String thisServer();

    @Key("advertised")
    String advertised();

    @Key("advertisedHint")
    String advertisedHint();

    @Key("resolver")
    String resolver();

    @Key("resolverHint")
    String resolverHint();

    @Key("dns")
    String dns();

    @Key("enableDns")
    String enableDns();

    @Key("dnsBind")
    String dnsBind();

    @Key("dnsPort")
    String dnsPort();

    @Key("dnsHint")
    String dnsHint();

    @Key("doh")
    String doh();

    @Key("enableDoh")
    String enableDoh();

    @Key("dohBind")
    String dohBind();

    @Key("dohPort")
    String dohPort();

    @Key("dohHint")
    String dohHint();

    @Key("doh3")
    String doh3();

    @Key("enableDoh3")
    String enableDoh3();

    @Key("doh3Bind")
    String doh3Bind();

    @Key("doh3Port")
    String doh3Port();

    @Key("doh3Hint")
    String doh3Hint();

    @Key("dot")
    String dot();

    @Key("enableDot")
    String enableDot();

    @Key("dotBind")
    String dotBind();

    @Key("dotPort")
    String dotPort();

    @Key("dotHint")
    String dotHint();

    @Key("doq")
    String doq();

    @Key("enableDoq")
    String enableDoq();

    @Key("doqBind")
    String doqBind();

    @Key("doqPort")
    String doqPort();

    @Key("doqHint")
    String doqHint();

    @Key("tcp")
    String tcp();

    @Key("enableTcp")
    String enableTcp();

    @Key("tcpUseUpstream")
    String tcpUseUpstream();

    @Key("tcpBind")
    String tcpBind();

    @Key("tcpPort")
    String tcpPort();

    @Key("tcpHint")
    String tcpHint();

    @Key("saveListeners")
    String saveListeners();

    @Key("routes")
    String routes();

    @Key("routeName")
    String routeName();

    @Key("routeNameHint")
    String routeNameHint();

    @Key("mode")
    String mode();

    @Key("upstreamHost")
    String upstreamHost();

    @Key("upstreamHostHint")
    String upstreamHostHint();

    @Key("upstreamPort")
    String upstreamPort();

    @Key("upstreamPortHint")
    String upstreamPortHint();

    @Key("answerIp")
    String answerIp();

    @Key("answerIpHint")
    String answerIpHint();

    @Key("saveRoute")
    String saveRoute();

    @Key("routeHint")
    String routeHint();

    @Key("recentQueries")
    String recentQueries();

    @Key("recentTcp")
    String recentTcp();

    @Key("allowedNetworks")
    String allowedNetworks();

    @Key("allowedNetworksHint")
    String allowedNetworksHint();

    @Key("network")
    String network();

    @Key("realAddress")
    String realAddress();

    @Key("colName")
    String colName();

    @Key("colMode")
    String colMode();

    @Key("colUpstream")
    String colUpstream();

    @Key("colAnswer")
    String colAnswer();

    @Key("colEnabled")
    String colEnabled();

    @Key("colTime")
    String colTime();

    @Key("colClient")
    String colClient();

    @Key("colProto")
    String colProto();

    @Key("colType")
    String colType();

    @Key("colResult")
    String colResult();

    @Key("colElapsed")
    String colElapsed();

    @Key("colHost")
    String colHost();

    @Key("noQueries")
    String noQueries();

    @Key("noTcpSessions")
    String noTcpSessions();

    @Key("networkSaved")
    String networkSaved();

    @Key("networkDeleted")
    String networkDeleted();

    @Key("listenersSaved")
    String listenersSaved();

    @Key("routeSaved")
    String routeSaved();

    @Key("routeDeleted")
    String routeDeleted();

    @Key("modeRelay")
    String modeRelay();

    @Key("modeAddress")
    String modeAddress();

    @Key("autoRefresh")
    String autoRefresh();

    @Key("autoRefreshInterval")
    String autoRefreshInterval();

    @Key("intervalOff")
    String intervalOff();

    @Key("intervalS1")
    String intervalS1();

    @Key("intervalS2")
    String intervalS2();

    @Key("intervalS3")
    String intervalS3();

    @Key("intervalS5")
    String intervalS5();

    @Key("intervalS10")
    String intervalS10();

    @Key("intervalS30")
    String intervalS30();
}
