package io.github.connellite.proxy.client.util;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.RpcRequestBuilder;

/**
 * Drops the page callback for two transport outcomes, so pages do not alert on them.
 * A 401 sends the browser to the login page. A lost connection (HTTP status 0, or no response
 * at all) shows {@link ConnectionLostNotice} once; further failures stay quiet until a request succeeds.
 */
public class SessionAwareRpcRequestBuilder extends RpcRequestBuilder {

    @Override
    protected void doSetCallback(RequestBuilder rb, final RequestCallback callback) {
        super.doSetCallback(rb, new RequestCallback() {
            @Override
            public void onResponseReceived(Request request, Response response) {
                int status = response.getStatusCode();
                if (status == Response.SC_UNAUTHORIZED) {
                    Window.Location.assign("login");
                    return;
                }
                if (status == 0) {
                    ConnectionLostNotice.lost();
                    return;
                }
                ConnectionLostNotice.restored();
                callback.onResponseReceived(request, response);
            }

            @Override
            public void onError(Request request, Throwable exception) {
                // ConnectionLostNotice.lost();
                callback.onError(request, exception);
            }
        });
    }
}
