package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.ui.ListBox;

/**
 * ListBox backed by a GWT-serializable enum.
 */
public class EnumListBox<E extends Enum<E>> extends ListBox {

    /**
     * Supplies the visible label for an enum value.
     */
    public interface Labels<E> {
        String label(E value);
    }

    private final Class<E> type;
    private final E fallback;

    public EnumListBox(E[] values, Labels<E> labels) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values required");
        }
        this.type = values[0].getDeclaringClass();
        this.fallback = values[0];
        for (E value : values) {
            addItem(labels.label(value), value.name());
        }
        setSelectedIndex(0);
    }

    public void select(E value) {
        if (value == null) {
            setSelectedIndex(0);
            return;
        }
        String name = value.name();
        for (int i = 0; i < getItemCount(); i++) {
            if (name.equals(getValue(i))) {
                setSelectedIndex(i);
                return;
            }
        }
        setSelectedIndex(0);
    }

    public E selected() {
        return selected(fallback);
    }

    public E selected(E defaultValue) {
        int index = getSelectedIndex();
        if (index < 0 || index >= getItemCount()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, getValue(index));
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }
}
