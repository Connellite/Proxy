package io.github.connellite.proxy.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.client.SafeHtmlTemplates.Template;
import com.google.gwt.safehtml.shared.SafeHtml;

public final class UiTemplates {

    public static final Headings HEADINGS = GWT.create(Headings.class);
    public static final Badges BADGES = GWT.create(Badges.class);

    private UiTemplates() {
    }

    public interface Headings extends SafeHtmlTemplates {

        @Template("<h1>{0}</h1>")
        SafeHtml h1(String text);

        @Template("<h2>{0}</h2>")
        SafeHtml h2(String text);

        @Template("<h3>{0}</h3>")
        SafeHtml h3(String text);
    }

    public interface Badges extends SafeHtmlTemplates {

        @Template("<span class=\"badge ok\">{0}</span>")
        SafeHtml ok(String text);

        @Template("<span class=\"badge muted\">{0}</span>")
        SafeHtml muted(String text);

        @Template("<span class=\"badge warn\">{0}</span>")
        SafeHtml warn(String text);

        @Template("<span class=\"badge admin\">{0}</span>")
        SafeHtml admin(String text);

        @Template("<span class=\"badge\">{0}</span>")
        SafeHtml plain(String text);
    }
}
