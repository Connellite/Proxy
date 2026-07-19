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
    /** Local bind choices: 0.0.0.0, 127.0.0.1, and current machine IPv4 addresses. */
    private List<String> bindHostOptions = new ArrayList<>();
}
