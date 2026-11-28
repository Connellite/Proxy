package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Settings for sockets this proxy opens to the internet or an upstream. Saved without restarting listeners.
 */
@Getter
@Setter
@NoArgsConstructor
public class OutboundSettingsGWTDTO implements IsSerializable {

    /** 0 = OS default; 1–255 = set IP TTL on proxy outbound sockets. */
    private int outboundTtl;
    private boolean clientHelloSplitEnabled;
    private ClientHelloSplitModeGWT clientHelloSplitMode = ClientHelloSplitModeGWT.FIXED;
    /** 1–1500, used by {@link ClientHelloSplitModeGWT#FIXED}. */
    private int clientHelloSplitPosition = 1;
    private boolean httpHeaderMangleEnabled;
}
