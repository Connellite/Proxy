package io.github.connellite.proxy.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * In-memory view of application settings loaded from the {@code config} key/value table.
 */
@Getter
@Setter
public class AppSettings {

    private boolean httpEnabled = false;

    private String httpBindHost = "0.0.0.0";

    private int httpPort = 3128;

    private boolean httpsEnabled = false;

    private String httpsBindHost = "0.0.0.0";

    private int httpsPort = 3129;

    /** Hostname/IP clients use for the HTTPS proxy. */
    private String httpsServerName;

    /** PEM certificate chain (inline), mutually exclusive with {@link #httpsCertificatePath}. */
    private String httpsCertificateChain;

    /** Path to PEM certificate chain file. */
    private String httpsCertificatePath;

    /** PEM private key (inline), mutually exclusive with {@link #httpsPrivateKeyPath}. */
    private String httpsPrivateKey;

    /** Path to PEM private key file. */
    private String httpsPrivateKeyPath;

    private boolean socksEnabled = false;

    private String socksBindHost = "0.0.0.0";

    private int socksPort = 1080;

    private boolean httpAuthRequired = false;

    private boolean httpsAuthRequired = false;

    private boolean socksAuthRequired = false;

    /** When true, SOCKS5 accepts UDP ASSOCIATE in addition to TCP CONNECT. */
    private boolean socksUdpEnabled = false;

    /** SSH tunnel / port-forward listener (Apache Mina SSHD). Password and user public keys via ProxyUser. */
    private boolean sshEnabled = false;

    private String sshBindHost = "0.0.0.0";

    private int sshPort = 2222;

    private boolean relayDnsEnabled = false;

    private String relayDnsBindHost = "0.0.0.0";

    private int relayDnsPort = 53;

    private boolean relayTcpEnabled = false;

    private String relayTcpBindHost = "0.0.0.0";

    private int relayTcpPort = 443;

    /** When true, new TCP relay connections use the selected upstream proxy if one is set. */
    private boolean relayTcpUseUpstream = false;

    private boolean relayDohEnabled = false;

    private String relayDohBindHost = "0.0.0.0";

    private int relayDohPort = 8443;

    private boolean relayDoqEnabled = false;

    private String relayDoqBindHost = "0.0.0.0";

    private int relayDoqPort = 853;

    /** Address DNS returns for relay-mode names. Empty until the operator saves one. */
    private String relayAdvertisedAddress;

    private String relayUpstreamResolver = "1.1.1.1:53";

    /**
     * IP TTL / IPv6 hop limit on sockets this proxy opens toward the internet or an upstream.
     * {@code 0} = OS default (no override).
     */
    private int outboundTtl = 0;

    private long bytesUpTotal = 0;

    private long bytesDownTotal = 0;
}
