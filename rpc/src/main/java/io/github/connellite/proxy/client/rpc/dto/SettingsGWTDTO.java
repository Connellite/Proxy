package io.github.connellite.proxy.client.rpc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SettingsGWTDTO implements IsSerializable {

    private boolean httpEnabled;
    private boolean socksEnabled;
    private boolean sshEnabled;
    private boolean httpAuthRequired;
    private boolean socksAuthRequired;
    private boolean socksUdpEnabled;
    private String httpBindHost;
    private String socksBindHost;
    private String sshBindHost;
    private int httpPort;
    private int socksPort;
    private int sshPort;
    private boolean httpRunning;
    private boolean httpsRunning;
    private boolean socksRunning;
    private boolean sshRunning;
    private String lastError;
    /** Local bind choices: 0.0.0.0, 127.0.0.1, and current machine IPv4 addresses. */
    private List<String> bindHostOptions = new ArrayList<>();
    /** Empty allows every address on HTTP, HTTPS, SOCKS, and SSH. */
    private List<RelayAllowRowGWTDTO> proxyAllows = new ArrayList<>();
}
