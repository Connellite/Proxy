package io.github.connellite.proxy.model;

#if SPRING_BOOT_3
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.type.TrueFalseConverter;
#else
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import org.hibernate.annotations.Type;
#endif
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "UPSTREAM_PROXIES")
public class UpstreamProxy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 16)
    @ColumnDefault("'HTTP'")
    private UpstreamProxyType type = UpstreamProxyType.HTTP;

    @Column(name = "HOST", nullable = false, length = 255)
    private String host;

    @Column(name = "PORT", nullable = false)
    private int port;

    @Column(name = "USERNAME", length = 128)
    private String username;

    @Column(name = "PASSWORD", length = 256)
    private String password;

    @Column(name = "SELECTED", nullable = false)
    @ColumnDefault("'F'")
#if SPRING_BOOT_3
    @Convert(converter = TrueFalseConverter.class)
#else
    @Type(type = "true_false")
#endif
    private boolean selected = false;

    @Column(name = "CREATED_AT", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant updatedAt;

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

    public boolean hasAuth() {
        return StringUtils.isNotBlank(username);
    }
}
