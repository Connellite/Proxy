package io.github.connellite.proxy.client.util;

import com.google.gwt.user.client.Window;

public final class Rpc {

    private Rpc() {
    }

    public static void showFailure(Throwable caught) {
        String message = caught == null ? "Request failed" : caught.getMessage();
        if (message == null || message.isEmpty()) {
            message = caught.getClass().getName();
        }
        Window.alert(message);
    }
}
