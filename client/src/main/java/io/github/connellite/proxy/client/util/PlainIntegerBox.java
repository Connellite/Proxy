package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.ui.TextBox;

/**
 * Integer field without locale grouping (avoids {@code 3,128} from {@code IntegerBox}).
 */
public class PlainIntegerBox extends TextBox {

    public PlainIntegerBox() {
        getElement().setAttribute("type", "number");
        getElement().setAttribute("inputmode", "numeric");
    }

    public void setIntValue(int value) {
        setText(Integer.toString(value));
    }

    public Integer getIntValue() {
        String text = getText();
        if (text == null) {
            return null;
        }
        text = text.trim().replace(",", "").replace(" ", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void setMin(int min) {
        getElement().setAttribute("min", Integer.toString(min));
    }

    public void setMax(int max) {
        getElement().setAttribute("max", Integer.toString(max));
    }
}
