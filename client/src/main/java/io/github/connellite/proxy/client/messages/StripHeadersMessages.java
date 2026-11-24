package io.github.connellite.proxy.client.messages;

import com.google.gwt.i18n.client.LocalizableResource.DefaultLocale;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;

@DefaultLocale("en")
public interface StripHeadersMessages extends Messages {

    @Key("title")
    String title();

    @Key("hint")
    String hint();

    @Key("namePlaceholder")
    String namePlaceholder();

    @Key("colName")
    String colName();

    @Key("colAction")
    String colAction();

    @Key("addHeader")
    String addHeader();

    @Key("removeHeader")
    String removeHeader(String name);

    @Key("enterName")
    String enterName();

    @Key("added")
    String added(String name);

    @Key("removed")
    String removed(String name);
}
