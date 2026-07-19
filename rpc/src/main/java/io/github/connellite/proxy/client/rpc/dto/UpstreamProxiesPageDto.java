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
public class UpstreamProxiesPageDto implements IsSerializable {

    private List<UpstreamProxyRowDto> proxies = new ArrayList<>();
    private long selectedId;
}
