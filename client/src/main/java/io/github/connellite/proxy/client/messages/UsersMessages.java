package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface UsersMessages extends Messages {

    @Key("title")
    String title();

    @Key("addUser")
    String addUser();

    @Key("rolesHint")
    String rolesHint();

    @Key("colUsername")
    String colUsername();

    @Key("colRoles")
    String colRoles();

    @Key("colStatus")
    String colStatus();

    @Key("colConn")
    String colConn();

    @Key("colSpeed")
    String colSpeed();

    @Key("colExpires")
    String colExpires();

    @Key("colTraffic")
    String colTraffic();

    @Key("colLastUsed")
    String colLastUsed();

    @Key("colActions")
    String colActions();

    @Key("noAccounts")
    String noAccounts();

    @Key("recentConnections")
    String recentConnections();

    @Key("colTime")
    String colTime();

    @Key("colUser")
    String colUser();

    @Key("colProto")
    String colProto();

    @Key("colClient")
    String colClient();

    @Key("colUp")
    String colUp();

    @Key("colDown")
    String colDown();

    @Key("colResult")
    String colResult();

    @Key("colElapsed")
    String colElapsed();

    @Key("noConnections")
    String noConnections();

    @Key("roleAdmin")
    String roleAdmin();

    @Key("roleUser")
    String roleUser();

    @Key("statusActive")
    String statusActive();

    @Key("statusDisabled")
    String statusDisabled();

    @Key("statusExpired")
    String statusExpired();

    @Key("statusQuota")
    String statusQuota();

    @Key("statusInactive")
    String statusInactive();

    @Key("ssh")
    String ssh();

    @Key("resetTraffic")
    String resetTraffic();

    @Key("disable")
    String disable();

    @Key("enable")
    String enable();

    @Key("resetTrafficConfirm")
    String resetTrafficConfirm(String userId);

    @Key("deleteUserConfirm")
    String deleteUserConfirm();

    @Key("trafficReset")
    String trafficReset();

    @Key("userDisabled")
    String userDisabled();

    @Key("userEnabled")
    String userEnabled();

    @Key("userDeleted")
    String userDeleted();

    @Key("titleNew")
    String titleNew();

    @Key("titleEdit")
    String titleEdit();

    @Key("titleUser")
    String titleUser();

    @Key("username")
    String username();

    @Key("password")
    String password();

    @Key("passwordKeep")
    String passwordKeep();

    @Key("adminUi")
    String adminUi();

    @Key("userProxy")
    String userProxy();

    @Key("roles")
    String roles();

    @Key("rolesFormHint")
    String rolesFormHint();

    @Key("maxConnections")
    String maxConnections();

    @Key("trafficLimit")
    String trafficLimit();

    @Key("trafficLimitHint")
    String trafficLimitHint();

    @Key("uploadLimit")
    String uploadLimit();

    @Key("uploadHint")
    String uploadHint();

    @Key("downloadLimit")
    String downloadLimit();

    @Key("downloadHint")
    String downloadHint();

    @Key("expires")
    String expires();

    @Key("userCreated")
    String userCreated();

    @Key("userUpdated")
    String userUpdated();
}
