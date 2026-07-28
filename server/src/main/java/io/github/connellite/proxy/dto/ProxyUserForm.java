package io.github.connellite.proxy.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProxyUserForm {

    private Long id;

    private String username;

    private String password;

    private boolean enabled = true;

    private int maxConnections;

    /** Total traffic cap in bytes; {@code < 0} = unlimited. */
    private long trafficLimitBytes = -1;

    /** Upload speed cap in bytes/sec; {@code < 0} = unlimited. */
    private long speedLimitUpBps = -1;

    /** Download speed cap in bytes/sec; {@code < 0} = unlimited. */
    private long speedLimitDownBps = -1;

    /** yyyy-MM-dd or empty */
    private String expiresAt;
}
