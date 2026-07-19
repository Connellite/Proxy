package io.github.connellite.proxy.client.rpc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.google.gwt.user.client.rpc.IsSerializable;

@Getter
@Setter
@NoArgsConstructor
public class UserFormDto implements IsSerializable {

    private Long id;
    private String username;
    private String password;
    private String expiresAt;
    private boolean enabled;
    private int maxConnections;
    private boolean creating;
}
