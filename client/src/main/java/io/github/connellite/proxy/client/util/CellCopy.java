package io.github.connellite.proxy.client.util;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Widget;

/**
 * Copies a table cell or {@code code} element into the clipboard on double-click.
 * Buttons and inputs keep their own behavior.
 */
public final class CellCopy {

    private CellCopy() {
    }

    public static void install(FlexTable table) {
        table.addDomHandler(new DoubleClickHandler() {
            @Override
            public void onDoubleClick(DoubleClickEvent event) {
                Element cell = cellOf(event.getNativeEvent().getEventTarget());
                if (cell == null || interactive(cell)) {
                    return;
                }
                flashCopy(cell, cell.getInnerText());
            }
        }, DoubleClickEvent.getType());
    }

    /**
     * Copies the text of a {@code code} element under {@code host} on double-click.
     */
    public static void installCode(Widget host) {
        host.addDomHandler(new DoubleClickHandler() {
            @Override
            public void onDoubleClick(DoubleClickEvent event) {
                Element code = codeOf(event.getNativeEvent().getEventTarget());
                if (code == null) {
                    return;
                }
                flashCopy(code, code.getInnerText());
            }
        }, DoubleClickEvent.getType());
    }

    private static void flashCopy(Element element, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        copyText(text);
        element.getStyle().setProperty("boxShadow", "inset 0 0 0 2px #2f6f4e");
        new Timer() {
            @Override
            public void run() {
                element.getStyle().clearProperty("boxShadow");
            }
        }.schedule(600);
    }

    private static boolean interactive(Element cell) {
        return cell.getElementsByTagName("button").getLength() > 0
                || cell.getElementsByTagName("input").getLength() > 0
                || cell.getElementsByTagName("textarea").getLength() > 0
                || cell.getElementsByTagName("select").getLength() > 0;
    }

    private static Element cellOf(EventTarget target) {
        Element element = elementOf(target);
        while (element != null) {
            String tag = element.getTagName();
            if ("td".equalsIgnoreCase(tag) || "th".equalsIgnoreCase(tag)) {
                return element;
            }
            if ("table".equalsIgnoreCase(tag)) {
                return null;
            }
            element = element.getParentElement();
        }
        return null;
    }

    private static Element codeOf(EventTarget target) {
        Element element = elementOf(target);
        while (element != null) {
            if ("code".equalsIgnoreCase(element.getTagName())) {
                return element;
            }
            element = element.getParentElement();
        }
        return null;
    }

    private static Element elementOf(EventTarget target) {
        if (Element.is(target)) {
            return Element.as(target);
        }
        if (Node.is(target)) {
            return Node.as(target).getParentElement();
        }
        return null;
    }

    private static native void copyText(String text) /*-{
        var area = $wnd.document.createElement("textarea");
        area.value = text;
        area.setAttribute("readonly", "readonly");
        area.style.position = "fixed";
        area.style.left = "-9999px";
        $wnd.document.body.appendChild(area);
        area.focus();
        area.select();
        try {
            $wnd.document.execCommand("copy");
        } catch (ignored) {
        }
        $wnd.document.body.removeChild(area);
    }-*/;
}
