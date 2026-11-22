package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RelayTcpSessionRowGWTDTO implements IsSerializable {

    private String time;
    private String client;
    private String hostname;
    private String outcome;
    private String elapsed;
}
