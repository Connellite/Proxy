package io.github.connellite.proxy.config;

import io.github.connellite.proxy.proxy.ssh.SshHostKeyType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    /** When empty: resolved from project folder ({@code .../data}). */
    private String dataDir = "";
    private boolean httpAuthRequired = false;
    private boolean httpsAuthRequired = false;
    private boolean socksAuthRequired = false;
    private boolean socksUdpEnabled = false;
    private int connectTimeoutMs = 15_000;
    /** Outbound IP TTL; 0 = OS default. Overridable in Settings UI. */
    private int outboundTtl = 0;
    private int idleTimeoutSeconds = 300;
    /** SSH server probes (OpenSSH ClientAlive). A peer silent for about {@link #idleTimeoutSeconds} is closed. */
    private boolean sshKeepaliveEnabled = true;
    /** How many probe intervals fit into {@link #idleTimeoutSeconds}. */
    private int sshKeepaliveCount = 5;
    private int httpMaxContentLengthBytes = 8 * 1024 * 1024;
    /** IANA zone id for admin UI dates (e.g. UTC, America/New_York). */
    private String timezone = "";
    private final Tls tls = new Tls();
    private final Bootstrap bootstrap = new Bootstrap();
    private final Listener http = new Listener(false, "0.0.0.0", 3128);
    private final Listener https = new Listener(false, "0.0.0.0", 3129);
    private final Listener socks5 = new Listener(false, "0.0.0.0", 1080);
    private final Ssh ssh = new Ssh();

    @Getter
    @Setter
    public static class Bootstrap {
        private String adminUsername = "admin";
        private String adminPassword = "admin";
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Listener {
        private boolean enabled;
        private String bindHost;
        private int port;
    }

    @Getter
    @Setter
    public static class Ssh extends Listener {
        /**
         * Host key types offered to clients, each kept in its own file under the data directory.
         */
        private List<SshHostKeyType> hostKeys = List.of(
                SshHostKeyType.SSH_ED25519, SshHostKeyType.ECDSA_SHA2_NISTP521, SshHostKeyType.SSH_RSA
        );

        public Ssh() {
            super(false, "0.0.0.0", 2222);
        }
    }

    /** TLS defaults: certificate/private-key paths (or paste PEM in the UI). */
    @Getter
    @Setter
    public static class Tls {
        private String serverName = "localhost";
        private String certificatePath = "";
        private String privateKeyPath = "";
    }
}
