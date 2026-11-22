package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.ui.ListBox;

import java.util.ArrayList;
import java.util.List;

/**
 * CCProxy-style bind-host dropdown.
 */
public class BindHostList extends ListBox {

    public BindHostList() {
        setVisibleItemCount(1);
    }

    public void fill(List<String> options, String selected) {
        clear();
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
            addItem(label(option), option);
            if (option.equals(value)) {
                found = true;
            }
        }
        if (!found) {
            addItem(label(value), value);
        }
        selectValue(value);
    }

    public String selected() {
        int index = getSelectedIndex();
        if (index < 0) {
            return "0.0.0.0";
        }
        return getValue(index);
    }

    private void selectValue(String value) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getValue(i).equals(value)) {
                setSelectedIndex(i);
                return;
            }
        }
        if (getItemCount() > 0) {
            setSelectedIndex(0);
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
