package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public final class Forms {

    private Forms() {
    }

    public static FlowPanel field(String caption, Widget input) {
        return field(caption, input, null);
    }

    public static FlowPanel field(String caption, Widget input, String hint) {
        FlowPanel result = new FlowPanel();
        result.getElement().getStyle().setProperty("marginBottom", "0.9rem");
        Label title = new Label(caption);
        title.getElement().getStyle().setProperty("fontWeight", "600");
        title.getElement().getStyle().setProperty("display", "block");
        title.getElement().getStyle().setProperty("marginBottom", "0.35rem");
        result.add(title);
        result.add(input);
        if (hint != null && !hint.isEmpty()) {
            Label hintLabel = new Label(hint);
            hintLabel.setStyleName("field-hint");
            result.add(hintLabel);
        }
        return result;
    }

    public static CheckBox checkbox(String text) {
        CheckBox box = new CheckBox(text);
        box.addStyleName("checkbox");
        return box;
    }

    public static FlowPanel twoCol(Widget left, Widget right) {
        FlowPanel row = new FlowPanel();
        row.setStyleName("two-col");
        row.add(left);
        row.add(right);
        return row;
    }

    public static FlowPanel formActions(Widget... widgets) {
        FlowPanel actions = new FlowPanel();
        actions.setStyleName("form-actions");
        for (Widget widget : widgets) {
            actions.add(widget);
        }
        return actions;
    }
}
