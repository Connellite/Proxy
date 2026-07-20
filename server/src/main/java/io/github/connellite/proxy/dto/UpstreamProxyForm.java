package io.github.connellite.proxy.dto;

import io.github.connellite.proxy.model.UpstreamProxyType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpstreamProxyForm {

    private String name;
    private UpstreamProxyType type;
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean updatePassword;
}
