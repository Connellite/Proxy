package io.github.connellite.proxy.client.rpc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.google.gwt.user.client.rpc.IsSerializable;

@Getter
@Setter
@NoArgsConstructor
public class EncryptionDto implements IsSerializable {

    private boolean httpsEnabled;
    private String httpsBindHost;
    private String serverName;
    private String certificateChain;
    private String certificatePath;
    private String privateKey;
    private String privateKeyPath;
    private int httpsPort;
    private boolean privateKeySaved;
    private boolean httpsRunning;
    private String lastError;
    private TlsStatusDto tlsStatus;
}
