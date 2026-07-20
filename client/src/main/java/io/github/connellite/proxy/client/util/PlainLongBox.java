package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.ui.TextBox;

/**
 * Long integer field without locale grouping.
 */
public class PlainLongBox extends TextBox {

    public PlainLongBox() {
        getElement().setAttribute("type", "number");
        getElement().setAttribute("inputmode", "numeric");
    }

    public void setLongValue(long value) {
        setText(Long.toString(value));
    }

    public Long getLongValue() {
        String text = getText();
        if (text == null) {
            return null;
        }
        text = text.trim().replace(",", "").replace(" ", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void setMin(long min) {
        getElement().setAttribute("min", Long.toString(min));
    }

    public void setMax(long max) {
        getElement().setAttribute("max", Long.toString(max));
    }
}
