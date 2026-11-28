package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.ui.FlexTable;

/**
 * Shared header row for the admin tables. The look lives in {@code .users-table tr.table-head}
 * in app.css; FlexTable writes header cells as {@code td}, so the {@code thead th} rules miss them.
 */
public final class Tables {

    private Tables() {
    }

    public static void header(FlexTable table, String... titles) {
        for (int i = 0; i < titles.length; i++) {
            table.setText(0, i, titles[i]);
        }
        table.getRowFormatter().setStyleName(0, "table-head");
    }
}
