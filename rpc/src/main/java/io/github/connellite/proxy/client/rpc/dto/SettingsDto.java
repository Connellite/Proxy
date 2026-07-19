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
    private int adminServerPort;
    private boolean httpRunning;
    private boolean httpsRunning;
    private boolean socksRunning;
    private String lastError;
    /** Local bind choices: 0.0.0.0, 127.0.0.1, and current machine IPv4 addresses. */
    private List<String> bindHostOptions = new ArrayList<>();
}
