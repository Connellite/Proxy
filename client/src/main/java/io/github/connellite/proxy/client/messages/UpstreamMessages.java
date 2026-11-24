package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface UpstreamMessages extends Messages {

    @Key("title")
    String title();

    @Key("useDirect")
    String useDirect();

    @Key("addUpstream")
    String addUpstream();

    @Key("hint")
    String hint();

    @Key("activeDirect")
    String activeDirect();

    @Key("activeNamed")
    String activeNamed(String name);

    @Key("colName")
    String colName();

    @Key("colType")
    String colType();

    @Key("colEndpoint")
    String colEndpoint();

    @Key("colAuth")
    String colAuth();

    @Key("colStatus")
    String colStatus();

    @Key("colActions")
    String colActions();

    @Key("selected")
    String selected();

    @Key("idle")
    String idle();

    @Key("noProxies")
    String noProxies();

    @Key("usingDirect")
    String usingDirect();

    @Key("upstreamSelected")
    String upstreamSelected(String name);

    @Key("deleteConfirm")
    String deleteConfirm(String name);

    @Key("deleted")
    String deleted();

    @Key("titleNew")
    String titleNew();

    @Key("titleEdit")
    String titleEdit();

    @Key("name")
    String name();

    @Key("type")
    String type();

    @Key("host")
    String host();

    @Key("port")
    String port();

    @Key("username")
    String username();

    @Key("password")
    String password();

    @Key("sshHint")
    String sshHint();

    @Key("passwordSaved")
    String passwordSaved();

    @Key("sshPasswordEmpty")
    String sshPasswordEmpty();

    @Key("passwordEmpty")
    String passwordEmpty();

    @Key("created")
    String created();

    @Key("updated")
    String updated();

    @Key("typeHttp")
    String typeHttp();

    @Key("typeHttps")
    String typeHttps();

    @Key("typeSocks5")
    String typeSocks5();

    @Key("typeSsh")
    String typeSsh();
}
