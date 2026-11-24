package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface SftpMessages extends Messages {

    @Key("title")
    String title();

    @Key("hint")
    String hint();

    @Key("sshDisabled")
    String sshDisabled();

    @Key("sshNotRunning")
    String sshNotRunning();

    @Key("colUser")
    String colUser();

    @Key("colFolder")
    String colFolder();

    @Key("colAccess")
    String colAccess();

    @Key("colActions")
    String colActions();

    @Key("noUsers")
    String noUsers();

    @Key("off")
    String off();

    @Key("userInactive")
    String userInactive();

    @Key("readOnly")
    String readOnly();

    @Key("readWrite")
    String readWrite();

    @Key("readOnlyTitle")
    String readOnlyTitle();

    @Key("folderPlaceholder")
    String folderPlaceholder();

    @Key("browse")
    String browse();

    @Key("disable")
    String disable();

    @Key("turnOffConfirm")
    String turnOffConfirm(String userId);

    @Key("turnedOff")
    String turnedOff(String userId);

    @Key("savedReadOnly")
    String savedReadOnly(String userId);

    @Key("savedReadWrite")
    String savedReadWrite(String userId);

    @Key("chooseFolder")
    String chooseFolder();

    @Key("newFolder")
    String newFolder();

    @Key("path")
    String path();

    @Key("selectFolderFirst")
    String selectFolderFirst();

    @Key("newFolderPrompt")
    String newFolderPrompt(String parent);
}
