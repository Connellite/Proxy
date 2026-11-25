package io.github.connellite.proxy.client.util;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.RpcRequestBuilder;

/**
 * Sends the browser to the login page when the server answers an RPC with 401 (session expired).
 * The pending callback is dropped, so pages do not show an error alert before the redirect.
 */
public class SessionAwareRpcRequestBuilder extends RpcRequestBuilder {

    @Override
    protected void doSetCallback(RequestBuilder rb, final RequestCallback callback) {
        super.doSetCallback(rb, new RequestCallback() {
            @Override
            public void onResponseReceived(Request request, Response response) {
                if (response.getStatusCode() == Response.SC_UNAUTHORIZED) {
                    Window.Location.assign("login");
                    return;
                }
                callback.onResponseReceived(request, response);
            }

            @Override
            public void onError(Request request, Throwable exception) {
                callback.onError(request, exception);
            }
        });
    }
}
