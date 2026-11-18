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
        consoleError(format(caught));
        Window.alert(message);
    }

    /** Cause chain included: GWT hides the real SerializationException behind a generic message. */
    private static String format(Throwable caught) {
        if (caught == null) {
            return "Request failed";
        }
        StringBuilder text = new StringBuilder();
        Throwable current = caught;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                text.append("\nCaused by: ");
            }
            text.append(current.getClass().getName());
            String detail = current.getMessage();
            if (detail != null && !detail.isEmpty()) {
                text.append(": ").append(detail);
            }
            StackTraceElement[] stack = current.getStackTrace();
            if (stack != null) {
                int limit = Math.min(stack.length, 12);
                for (int i = 0; i < limit; i++) {
                    text.append("\n    at ").append(stack[i]);
                }
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
            depth++;
        }
        return text.toString();
    }

    private static native void consoleError(String message) /*-{
        if ($wnd.console && $wnd.console.error) {
            $wnd.console.error(message);
        }
    }-*/;
}
