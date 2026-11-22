package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RelayRouteRowGWTDTO implements IsSerializable {

    private Long id;
    private String name;
    private RelayRouteModeGWT mode;
    private String upstreamHost;
    /** Empty when the relay should use the port the client connected to. */
    private String upstreamPort;
    private String answerAddress;
    private boolean enabled;
}
