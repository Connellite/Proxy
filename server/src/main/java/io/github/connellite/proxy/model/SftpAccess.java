package io.github.connellite.proxy.model;

#if SPRING_BOOT_3
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.type.TrueFalseConverter;
#else
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import org.hibernate.annotations.Type;
#endif
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * SFTP access of one proxy user: a server folder exposed as {@code /} over SFTP.
 * No row means SFTP is off for that user. Keyed by the user id ({@code USR_ID}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SFTP_ACCESS")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class SftpAccess {

    @Id
    @Column(name = "USR_ID", length = 64)
    private String userId;

    /** Absolute real path of the folder on the server. */
    @Column(name = "ROOT_PATH", nullable = false, length = 1024)
    private String rootPath;

    /** When true, the user can list and download but not upload, change, rename or delete. */
    @Column(name = "READ_ONLY", nullable = false)
    @ColumnDefault("'F'")
#if SPRING_BOOT_3
    @Convert(converter = TrueFalseConverter.class)
#else
    @Type(type = "true_false")
#endif
    private boolean readOnly = false;

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
}
