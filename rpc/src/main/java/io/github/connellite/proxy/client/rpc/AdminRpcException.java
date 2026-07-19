package io.github.connellite.proxy.client.rpc;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.NoArgsConstructor;

/**
 * Checked failure returned to the GWT admin UI.
 */
@NoArgsConstructor
public class AdminRpcException extends Exception implements IsSerializable {

    public AdminRpcException(String message) {
        super(message);
    }
}
