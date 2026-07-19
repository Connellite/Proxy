package io.github.connellite.proxy.client.rpc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.google.gwt.user.client.rpc.IsSerializable;

@Getter
@Setter
@NoArgsConstructor
public class SettingsDto implements IsSerializable {

    private boolean httpEnabled;
    private boolean socksEnabled;
    private boolean httpAuthRequired;
    private boolean socksAuthRequired;
    private boolean socksUdpEnabled;
    private String httpBindHost;
    private String socksBindHost;
    private int httpPort;
    private int socksPort;
    private boolean httpRunning;
    private boolean httpsRunning;
    private boolean socksRunning;
    private String lastError;
}
