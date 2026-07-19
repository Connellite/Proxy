package io.github.connellite.proxy.client.util;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;

/**
 * Auto-refresh control matching the old Thymeleaf refresh selector.
 */
public class AutoRefresh extends Composite {

    public interface Callback {
        void onRefresh();
    }

    private final Callback callback;
    private final ListBox interval = new ListBox();
    private Timer timer;

    public AutoRefresh(Callback callback) {
        this.callback = callback;
        FlowPanel root = new FlowPanel();
        root.setStyleName("refresh-control");
        // use Label as visual; HTML label wrapping is awkward in GWT
        Label caption = new Label("Auto-refresh");
        interval.getElement().setAttribute("aria-label", "Auto-refresh interval");
        interval.addItem("Off", "0");
        interval.addItem("1s", "1");
        interval.addItem("2s", "2");
        interval.addItem("3s", "3");
        interval.addItem("5s", "5");
        interval.addItem("10s", "10");
        interval.addItem("30s", "30");
        interval.setSelectedIndex(3); // 3s default like old JS
        interval.addChangeHandler(new ChangeHandler() {
            @Override
            public void onChange(ChangeEvent event) {
                restart();
            }
        });
        root.add(caption);
        root.add(interval);
        initWidget(root);
        restart();
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void restart() {
        stop();
        int seconds = Integer.parseInt(interval.getSelectedValue());
        if (seconds <= 0) {
            return;
        }
        timer = new Timer() {
            @Override
            public void run() {
                callback.onRefresh();
            }
        };
        timer.scheduleRepeating(seconds * 1000);
    }
}
