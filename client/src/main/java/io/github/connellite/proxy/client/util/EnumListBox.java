package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.ui.ListBox;
import io.github.connellite.proxy.client.rpc.dto.HasGwtLabel;

/**
 * ListBox backed by a GWT-serializable enum ({@link HasGwtLabel}).
 */
public class EnumListBox<E extends Enum<E> & HasGwtLabel> extends ListBox {

    private final Class<E> type;
    private final E fallback;

    public EnumListBox(E[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values required");
        }
        this.type = values[0].getDeclaringClass();
        this.fallback = values[0];
        for (E value : values) {
            addItem(value.getLabel(), value.name());
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
