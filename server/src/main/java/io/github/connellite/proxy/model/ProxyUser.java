package io.github.connellite.proxy.model;

#if SPRING_BOOT_3
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.type.TrueFalseConverter;
#else
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import org.hibernate.annotations.Type;
#endif
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@Table(name = "PROXY_USERS")
public class ProxyUser implements UserDetails {

    @Id
    @Column(name = "USR_ID", length = 64)
    private String id = "";

    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "ENABLED", nullable = false)
    @ColumnDefault("'T'")
#if SPRING_BOOT_3
    @Convert(converter = TrueFalseConverter.class)
#else
    @Type(type = "true_false")
#endif
    private boolean enabled = true;

    /** 0 = unlimited */
    @Column(name = "MAX_CONNECTIONS", nullable = false)
    @ColumnDefault("0")
    private int maxConnections = 0;

    /**
     * Max total traffic ({@code bytesUp + bytesDown}) in bytes.
     * Values {@code < 0} mean unlimited (stored as {@code -1}).
     */
    @Column(name = "TRAFFIC_LIMIT_BYTES", nullable = false)
    @ColumnDefault("-1")
    private long trafficLimitBytes = -1;

    /**
     * Max upload speed in bytes/sec (client → proxy).
     * Values {@code < 0} mean unlimited (stored as {@code -1}).
     */
    @Column(name = "SPEED_LIMIT_UP_BPS", nullable = false)
    @ColumnDefault("-1")
    private long speedLimitUpBps = -1;

    /**
     * Max download speed in bytes/sec (proxy → client).
     * Values {@code < 0} mean unlimited (stored as {@code -1}).
     */
    @Column(name = "SPEED_LIMIT_DOWN_BPS", nullable = false)
    @ColumnDefault("-1")
    private long speedLimitDownBps = -1;

    @Column(name = "EXPIRES_AT")
    private Instant expiresAt;

    @Column(name = "BYTES_UP", nullable = false)
    @ColumnDefault("0")
    private long bytesUp = 0;

    @Column(name = "BYTES_DOWN", nullable = false)
    @ColumnDefault("0")
    private long bytesDown = 0;

    @Column(name = "CREATED_AT", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant updatedAt;

    @Column(name = "LAST_USED_AT")
    private Instant lastUsedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "USER_ROLE",
            joinColumns = @JoinColumn(name = "USERNAME", referencedColumnName = "USR_ID"),
            inverseJoinColumns = @JoinColumn(name = "UR_ROLE", referencedColumnName = "ROL_ID"))
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isTrafficLimitExceeded() {
        return trafficLimitBytes >= 0 && (bytesUp + bytesDown) >= trafficLimitBytes;
    }

    public boolean isUsable() {
        return enabled && !isExpired() && !isTrafficLimitExceeded();
    }

    public boolean hasRole(String roleId) {
        if (roleId == null || roles == null) {
            return false;
        }
        for (Role role : roles) {
            if (role != null && role.isActive() && roleId.equals(role.getId())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAdminRole() {
        return hasRole(Role.ADMIN);
    }

    public boolean hasUserRole() {
        return hasRole(Role.USER);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .filter(role -> role != null && role.isActive() && role.getId() != null)
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return id;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !isExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
