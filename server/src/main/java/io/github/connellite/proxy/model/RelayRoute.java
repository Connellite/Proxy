package io.github.connellite.proxy.model;

#if SPRING_BOOT_3
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.type.TrueFalseConverter;
#else
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.hibernate.annotations.Type;
#endif
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.ColumnDefault;

/**
 * One DNS name. {@link RelayRouteMode#RELAY} sends clients to this server.
 * {@link RelayRouteMode#ADDRESS} answers with {@link #answerAddress} and is not accepted by the TCP relay.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "RELAY_ROUTE", uniqueConstraints = @UniqueConstraint(name = "UK_RELAY_ROUTE_NAME", columnNames = "NAME"))
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class RelayRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** Lower-case name or a mask with a single leading wildcard label. */
    @Column(name = "NAME", nullable = false, length = 253)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "MODE", nullable = false, length = 16)
    private RelayRouteMode mode;

    @Column(name = "UPSTREAM_HOST", length = 253)
    private String upstreamHost;

    @Column(name = "UPSTREAM_PORT")
    private Integer upstreamPort;

    @Column(name = "ANSWER_ADDRESS", length = 64)
    private String answerAddress;

    @Column(name = "ENABLED", nullable = false)
    @ColumnDefault("'T'")
#if SPRING_BOOT_3
    @Convert(converter = TrueFalseConverter.class)
#else
    @Type(type = "true_false")
#endif
    private boolean enabled = true;
}
