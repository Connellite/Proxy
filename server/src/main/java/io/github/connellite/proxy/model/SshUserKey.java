package io.github.connellite.proxy.model;

#if SPRING_BOOT_3
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
#else
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
#endif
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * Public half of an SSH user key. The private half is returned once at issuance and is not stored.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SSH_USER_KEY", uniqueConstraints = @UniqueConstraint(
        name = "UK_SSH_USER_KEY_USER_FP",
        columnNames = {"USR_ID", "FINGERPRINT"}))
public class SshUserKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USR_ID", nullable = false)
    private ProxyUser user;

    /** OpenSSH public key line ({@code ssh-ed25519 AAAA...}) without a comment. */
    @Column(name = "PUBLIC_KEY", nullable = false, length = 512)
    private String publicKey;

    /** OpenSSH SHA256 fingerprint, for example {@code SHA256:...}. */
    @Column(name = "FINGERPRINT", nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "COMMENT", length = 256)
    private String comment;

    @Column(name = "CREATED_AT", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
