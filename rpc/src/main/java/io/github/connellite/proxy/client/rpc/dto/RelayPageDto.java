package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RelayPageDto implements IsSerializable {

    private boolean dnsEnabled;
    private String dnsBindHost;
    private int dnsPort;
    private boolean tcpEnabled;
    private String tcpBindHost;
    private int tcpPort;
    /** Address returned for relay-mode names. */
    private String advertisedAddress;
    private String upstreamResolver;
    private List<String> bindHostOptions = new ArrayList<>();
    private List<RelayRouteRowDto> routes = new ArrayList<>();
}
