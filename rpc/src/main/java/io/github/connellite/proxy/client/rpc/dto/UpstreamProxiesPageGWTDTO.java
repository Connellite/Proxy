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
public class UpstreamProxiesPageGWTDTO implements IsSerializable {

    private List<UpstreamProxyRowGWTDTO> proxies = new ArrayList<>();
    private long selectedId;
    private OutboundSettingsGWTDTO outbound = new OutboundSettingsGWTDTO();
}
