package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserConnectionRowGWTDTO implements IsSerializable {

    private String time;
    private String user;
    private String protocol;
    private String client;
    private long bytesUp;
    private long bytesDown;
    private String outcome;
    private String elapsed;
}
