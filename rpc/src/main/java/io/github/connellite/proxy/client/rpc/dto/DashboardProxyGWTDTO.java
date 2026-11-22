package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DashboardProxyGWTDTO implements IsSerializable {

    private boolean httpRunning;
    private boolean httpsRunning;
    private boolean socksRunning;
    private boolean sshRunning;
    private String httpBind;
    private String httpsBind;
    private String socksBind;
    private String sshBind;
    private int httpPort;
    private int httpsPort;
    private int socksPort;
    private int sshPort;
    private int activeConnections;
    private int userCount;
    private int enabledUsers;
    private String lastError;
    private long totalBytesUp;
    private long totalBytesDown;
    private long sessionBytesUp;
    private long sessionBytesDown;
}
