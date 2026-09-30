package io.github.connellite.proxy.model;

#if SPRING_BOOT_3
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.type.TrueFalseConverter;
#else
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Type;
#endif
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

@Getter
@Setter
@Entity
@Table(name = "PROXY_ROLE")
public class Role implements GrantedAuthority {

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String USER = "ROLE_USER";

    @Id
    @Column(name = "ROL_ID", length = 64)
    private String id = "";

    @Column(name = "ROL_ACTIVE")
#if SPRING_BOOT_3
    @Convert(converter = TrueFalseConverter.class)
#else
    @Type(type = "true_false")
#endif
    private boolean active = false;

    @Override
    public String getAuthority() {
        return id;
    }
}
