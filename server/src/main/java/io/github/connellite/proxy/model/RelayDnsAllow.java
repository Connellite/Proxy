package io.github.connellite.proxy.model;

#if SPRING_BOOT_3
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
#else
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
#endif
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** One network allowed to query DNS, DoH, DoH3, DoT, and DoQ. An empty table allows every address. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "RELAY_DNS_ALLOW", uniqueConstraints = @UniqueConstraint(name = "UK_RELAY_DNS_ALLOW_CIDR", columnNames = "CIDR"))
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class RelayDnsAllow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CIDR", nullable = false, length = 64)
    private String cidr;
}
