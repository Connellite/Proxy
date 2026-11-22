package io.github.connellite.proxy.client.util;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FlexTable;

/**
 * Copies a table cell into the clipboard on double-click. Buttons and inputs keep their own behavior.
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
                copyText(cell.getInnerText());
                cell.getStyle().setProperty("boxShadow", "inset 0 0 0 2px #2f6f4e");
                new Timer() {
                    @Override
                    public void run() {
                        cell.getStyle().clearProperty("boxShadow");
                    }
                }.schedule(600);
            }
        }, DoubleClickEvent.getType());
    }

    private static boolean interactive(Element cell) {
        return cell.getElementsByTagName("button").getLength() > 0
                || cell.getElementsByTagName("input").getLength() > 0
                || cell.getElementsByTagName("textarea").getLength() > 0
                || cell.getElementsByTagName("select").getLength() > 0;
    }

    private static Element cellOf(EventTarget target) {
        Element element;
        if (Element.is(target)) {
            element = Element.as(target);
        } else if (Node.is(target)) {
            element = Node.as(target).getParentElement();
        } else {
            return null;
        }
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
