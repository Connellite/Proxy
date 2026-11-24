package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface EncryptionMessages extends Messages {

    @Key("title")
    String title();

    @Key("enableHttps")
    String enableHttps();

    @Key("httpsAuth")
    String httpsAuth();

    @Key("httpsBind")
    String httpsBind();

    @Key("httpsPort")
    String httpsPort();

    @Key("serverName")
    String serverName();

    @Key("serverNameHint")
    String serverNameHint();

    @Key("serverNamePlaceholder")
    String serverNamePlaceholder();

    @Key("certificates")
    String certificates();

    @Key("certificatesHint")
    String certificatesHint();

    @Key("certificatePlaceholder")
    String certificatePlaceholder();

    @Key("certificatePath")
    String certificatePath();

    @Key("certificatePathHint")
    String certificatePathHint();

    @Key("certificatePathPlaceholder")
    String certificatePathPlaceholder();

    @Key("privateKey")
    String privateKey();

    @Key("privateKeyHint")
    String privateKeyHint();

    @Key("privateKeySavedPlaceholder")
    String privateKeySavedPlaceholder();

    @Key("privateKeyEmptyPlaceholder")
    String privateKeyEmptyPlaceholder();

    @Key("privateKeySavedHint")
    String privateKeySavedHint();

    @Key("privateKeyPath")
    String privateKeyPath();

    @Key("privateKeyPathHint")
    String privateKeyPathHint();

    @Key("privateKeyPathPlaceholder")
    String privateKeyPathPlaceholder();

    @Key("saveSettings")
    String saveSettings();

    @Key("backToSettings")
    String backToSettings();

    @Key("httpsRuntime")
    String httpsRuntime(String state);

    @Key("saved")
    String saved();

    @Key("certificateStatus")
    String certificateStatus();

    @Key("certificateFlag")
    String certificateFlag(String state);

    @Key("privateKeyFlag")
    String privateKeyFlag(String state);

    @Key("privateKeyFlagTyped")
    String privateKeyFlagTyped(String state, String keyType);

    @Key("pairFlag")
    String pairFlag(String state);

    @Key("chainFlag")
    String chainFlag(String state);

    @Key("valid")
    String valid();

    @Key("invalid")
    String invalid();

    @Key("subject")
    String subject();

    @Key("issuer")
    String issuer();

    @Key("validity")
    String validity(String from, String to);

    @Key("names")
    String names();
}
