package io.github.connellite.proxy.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EncryptionForm {

    private boolean httpsEnabled = false;

    private String httpsBindHost = "0.0.0.0";

    private int httpsPort = 3129;

    private String serverName = "";

    private String certificateChain;

    private String certificatePath;

    private String privateKey;

    private String privateKeyPath;

    /** UI-only: existing private key is stored and not echoed back. */
    private boolean privateKeySaved;
}
