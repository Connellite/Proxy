package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.ui.ListBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for CCProxy-style bind-host dropdowns.
 */
public final class BindHostList {

    private BindHostList() {
    }

    public static ListBox create() {
        ListBox box = new ListBox();
        box.setVisibleItemCount(1);
        return box;
    }

    public static void fill(ListBox box, List<String> options, String selected) {
        box.clear();
        List<String> values = options != null ? options : new ArrayList<>();
        if (values.isEmpty()) {
            values = new ArrayList<>();
            values.add("0.0.0.0");
            values.add("127.0.0.1");
        }
        String value = selected == null || selected.trim().isEmpty() ? "0.0.0.0" : selected.trim();
        boolean found = false;
        for (String option : values) {
            if (option == null || option.isEmpty()) {
                continue;
            }
            box.addItem(label(option), option);
            if (option.equals(value)) {
                found = true;
            }
        }
        if (!found) {
            box.addItem(label(value), value);
        }
        selectValue(box, value);
    }

    public static String selected(ListBox box) {
        int index = box.getSelectedIndex();
        if (index < 0) {
            return "0.0.0.0";
        }
        return box.getValue(index);
    }

    private static void selectValue(ListBox box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getValue(i).equals(value)) {
                box.setSelectedIndex(i);
                return;
            }
        }
        if (box.getItemCount() > 0) {
            box.setSelectedIndex(0);
        }
    }

    private static String label(String address) {
        if ("0.0.0.0".equals(address)) {
            return "0.0.0.0 (all interfaces)";
        }
        if ("127.0.0.1".equals(address)) {
            return "127.0.0.1 (localhost)";
        }
        return address;
    }
}
