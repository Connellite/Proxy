package io.github.connellite.proxy.client.util;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import io.github.connellite.proxy.client.messages.AppMessages;

/**
 * One non-modal notice for a lost RPC connection. The first loss opens it; closing it leaves
 * {@code offline} set, so later failures stay quiet until a request succeeds.
 */
public class ConnectionLostNotice extends PopupPanel {

    private static final ConnectionLostNotice NOTICE = new ConnectionLostNotice();
    private static volatile boolean offline;

    private ConnectionLostNotice() {
        super(false, false);
        Label message = new Label(AppMessages.COMMON.connectionLost());
        message.setStyleName("connection-lost-text");
        Button close = new Button(AppMessages.COMMON.close());
        close.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                hide();
            }
        });
        FlowPanel box = new FlowPanel();
        box.add(message);
        // box.add(close);
        setStyleName("connection-lost");
        setWidget(box);
    }

    /** First loss opens the notice. Closing it does not clear {@code offline}, so it stays hidden. */
    public static void lost() {
        if (offline) {
            return;
        }
        offline = true;
        NOTICE.center();
    }

    /** A response from the server means the connection is back, so the notice can go. */
    public static void restored() {
        if (!offline) {
            return;
        }
        offline = false;
        NOTICE.hide();
    }
}
