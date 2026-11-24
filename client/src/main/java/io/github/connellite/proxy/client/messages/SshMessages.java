package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface SshMessages extends Messages {

    @Key("title")
    String title();

    @Key("allowPassword")
    String allowPassword();

    @Key("passwordHint")
    String passwordHint();

    @Key("keys")
    String keys();

    @Key("keysHint")
    String keysHint();

    @Key("colComment")
    String colComment();

    @Key("colFingerprint")
    String colFingerprint();

    @Key("colCreated")
    String colCreated();

    @Key("colActions")
    String colActions();

    @Key("noKeys")
    String noKeys();

    @Key("revoke")
    String revoke();

    @Key("thisKey")
    String thisKey();

    @Key("revokeConfirm")
    String revokeConfirm(String label);

    @Key("keyRevoked")
    String keyRevoked();

    @Key("passwordEnabled")
    String passwordEnabled();

    @Key("passwordDisabled")
    String passwordDisabled();

    @Key("issueKey")
    String issueKey();

    @Key("comment")
    String comment();

    @Key("commentHint")
    String commentHint();

    @Key("passphrase")
    String passphrase();

    @Key("passphraseHint")
    String passphraseHint();

    @Key("issue")
    String issue();

    @Key("privateKey")
    String privateKey();

    @Key("download")
    String download();

    @Key("backToUsers")
    String backToUsers();

    @Key("keyIssued")
    String keyIssued();

    @Key("shownOnce")
    String shownOnce();

    @Key("encryptedFile")
    String encryptedFile();

    @Key("fingerprint")
    String fingerprint(String fingerprint);
}
