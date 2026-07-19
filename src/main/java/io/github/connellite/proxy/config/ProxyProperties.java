package io.github.connellite.proxy.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    private String dataDir = "./data";
    private boolean httpAuthRequired = false;
    private boolean socksAuthRequired = false;
    private int connectTimeoutMs = 15_000;
    private int idleTimeoutSeconds = 300;
    private int httpMaxContentLengthBytes = 8 * 1024 * 1024;
    /** IANA zone id for admin UI dates (e.g. UTC, America/New_York). */
    private String timezone = "";
    private final Bootstrap bootstrap = new Bootstrap();
    private final Listener http = new Listener(true, "0.0.0.0", 3128);
    private final Listener https = new Listener(false, "0.0.0.0", 3129);
    private final Listener socks5 = new Listener(true, "0.0.0.0", 1080);

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
}
