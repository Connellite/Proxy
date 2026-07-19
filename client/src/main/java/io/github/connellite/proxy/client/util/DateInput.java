package io.github.connellite.proxy.client.util;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.TextBox;

/**
 * Native HTML5 date picker ({@code <input type="date">}).
 * Opens the calendar when clicking anywhere on the field, not only the icon.
 */
public class DateInput extends TextBox {

    public DateInput() {
        getElement().setAttribute("type", "date");
        addStyleName("date-input");
        addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                openPicker(getElement());
            }
        });
    }

    /** @return yyyy-MM-dd or empty string */
    public String getDateValue() {
        String value = getText();
        return value == null ? "" : value.trim();
    }

    public void setDateValue(String yyyyMmDd) {
        setText(yyyyMmDd == null ? "" : yyyyMmDd);
    }

    private static native void openPicker(Element input) /*-{
        if (!input || typeof input.showPicker !== 'function') {
            return;
        }
        try {
            input.showPicker();
        } catch (e) {
            // Ignored: some browsers require a trusted gesture or disallow showPicker.
        }
    }-*/;
}
