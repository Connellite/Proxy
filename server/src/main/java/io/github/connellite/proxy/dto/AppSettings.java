package io.github.connellite.proxy.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * In-memory view of application settings loaded from the {@code config} key/value table.
 */
@Getter
@Setter
public class AppSettings {

    private boolean httpEnabled = true;

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

    private boolean socksEnabled = true;

    private String socksBindHost = "0.0.0.0";

    private int socksPort = 1080;

    private boolean httpAuthRequired = false;

    private boolean socksAuthRequired = false;

    /** When true, SOCKS5 accepts UDP ASSOCIATE in addition to TCP CONNECT. */
    private boolean socksUdpEnabled = false;

    /** SSH tunnel / port-forward listener (Apache Mina SSHD). Always password-auth via ProxyUser. */
    private boolean sshEnabled = false;

    private String sshBindHost = "0.0.0.0";

    private int sshPort = 2222;

    /** Spring Boot admin UI / Tomcat port ({@code server.port}). Requires app restart. */
    private int adminServerPort = 8080;

    private long bytesUpTotal = 0;

    private long bytesDownTotal = 0;
}
