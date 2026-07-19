package io.github.connellite.proxy.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * TLS validation status for the configured certificate chain and private key.
 */
@Getter
@Setter
public class TlsStatus {

    private boolean usingCustomCertificate;
    private boolean validCert;
    private boolean validKey;
    private boolean validChain;
    private boolean validPair;
    private boolean privateKeySaved;
    private String keyType;
    private String subject;
    private String issuer;
    private Instant notBefore;
    private Instant notAfter;
    private List<String> dnsNames = new ArrayList<>();
    private String warningValidation;
}
