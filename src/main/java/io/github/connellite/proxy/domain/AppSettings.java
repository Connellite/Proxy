package io.github.connellite.proxy.domain;

#if SPRING_BOOT_3
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
#else
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
#endif
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "app_settings")
public class AppSettings {

    public static final String SINGLETON_ID = "default";

    @Id
    private String id = SINGLETON_ID;

    @Column(name = "http_enabled", nullable = false)
    private boolean httpEnabled = true;

    @Column(name = "http_bind_host", nullable = false, length = 64)
    private String httpBindHost = "0.0.0.0";

    @Column(name = "http_port", nullable = false)
    private int httpPort = 3128;

    @Column(name = "socks_enabled", nullable = false)
    private boolean socksEnabled = true;

    @Column(name = "socks_bind_host", nullable = false, length = 64)
    private String socksBindHost = "0.0.0.0";

    @Column(name = "socks_port", nullable = false)
    private int socksPort = 1080;

    @Column(name = "auth_required", nullable = false)
    private boolean authRequired = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
