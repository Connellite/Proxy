package io.github.connellite.proxy.client.rpc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.google.gwt.user.client.rpc.IsSerializable;
import java.util.ArrayList;

@Getter
@Setter
@NoArgsConstructor
public class TlsStatusDto implements IsSerializable {

    private boolean usingCustomCertificate;
    private boolean validCert;
    private boolean validKey;
    private boolean validChain;
    private boolean validPair;
    private boolean privateKeySaved;
    private String keyType;
    private String subject;
    private String issuer;
    private String notBefore;
    private String notAfter;
    private String warningValidation;
    private ArrayList<String> dnsNames = new ArrayList<>();
}
