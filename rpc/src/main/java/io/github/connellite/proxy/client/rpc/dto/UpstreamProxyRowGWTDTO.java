package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpstreamProxyRowGWTDTO implements IsSerializable {

    private long id;
    private String name;
    private UpstreamProxyTypeGWT type;
    private String host;
    private int port;
    private String username;
    private boolean selected;
    private boolean authEnabled;
}
