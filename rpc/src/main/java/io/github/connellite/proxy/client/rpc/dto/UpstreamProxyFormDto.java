package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpstreamProxyFormDto implements IsSerializable {

    private Long id;
    private String name;
    private String type;
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean passwordSaved;
    private boolean creating;
}
