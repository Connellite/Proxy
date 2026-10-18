package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SshIssuedKeyDto implements IsSerializable {

    private long id;
    private String fingerprint;
    private String publicKey;
    private String privateKey;
    private boolean encrypted;
}
