package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRowDto implements IsSerializable {

    private long id;
    private String username;
    private boolean enabled;
    private boolean usable;
    private boolean expired;
    private int maxConnections;
    private String expiresAt;
    private long bytesUp;
    private long bytesDown;
    private int activeConnections;
    private long upBps;
    private long downBps;
    private String lastUsedAt;
}
