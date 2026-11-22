package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DashboardDnsDto implements IsSerializable {

    private boolean dnsRunning;
    private boolean tcpRunning;
    private boolean dohRunning;
    private boolean doh3Running;
    private boolean dotRunning;
    private boolean doqRunning;
    private String dnsBind;
    private String tcpBind;
    private String dohBind;
    private String doh3Bind;
    private String dotBind;
    private String doqBind;
    private int dnsPort;
    private int tcpPort;
    private int dohPort;
    private int doh3Port;
    private int dotPort;
    private int doqPort;
    private int routeCount;
    private int enabledRouteCount;
    private String advertisedAddress;
    private String upstreamResolver;
    private boolean tcpUseUpstream;
}
