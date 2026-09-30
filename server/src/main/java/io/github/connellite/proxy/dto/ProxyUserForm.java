package io.github.connellite.proxy.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProxyUserForm {

    /** Login / PK ({@code USR_ID}). */
    private String id;

    private String password;

    private boolean enabled = true;

    /** Grant {@code ROLE_ADMIN} (admin UI login). */
    private boolean roleAdmin;

    /** Grant {@code ROLE_USER} (proxy access). */
    private boolean roleUser;

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
