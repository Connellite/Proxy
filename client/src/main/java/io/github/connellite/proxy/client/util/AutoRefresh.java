package io.github.connellite.proxy.client.util;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import io.github.connellite.proxy.client.rpc.dto.AutoRefreshIntervalGWT;

/**
 * Auto-refresh control matching the old Thymeleaf refresh selector.
 */
public class AutoRefresh extends Composite {

    public interface Callback {
        void onRefresh();
    }

    private final Callback callback;
    private final EnumListBox<AutoRefreshIntervalGWT> interval =
            new EnumListBox<>(AutoRefreshIntervalGWT.values());
    private Timer timer;

    public AutoRefresh(Callback callback) {
        this.callback = callback;
        FlowPanel root = new FlowPanel();
        root.setStyleName("refresh-control");
        Label caption = new Label("Auto-refresh");
        interval.getElement().setAttribute("aria-label", "Auto-refresh interval");
        interval.select(AutoRefreshIntervalGWT.defaultInterval());
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
        int seconds = interval.selected(AutoRefreshIntervalGWT.OFF).getSeconds();
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
