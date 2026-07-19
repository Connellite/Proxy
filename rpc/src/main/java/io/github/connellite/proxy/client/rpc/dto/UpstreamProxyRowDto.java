package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpstreamProxyRowDto implements IsSerializable {

    private long id;
    private String name;
    private String type;
    private String host;
    private int port;
    private String username;
    private boolean selected;
    private boolean hasAuth;
}
