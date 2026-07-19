package io.github.connellite.proxy.model;

#if SPRING_BOOT_3
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
#else
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import org.hibernate.annotations.Type;
#endif
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "config")
public class ConfigEntry {

    public static final String HTTP_ENABLED = "http_enabled";
    public static final String HTTP_BIND_HOST = "http_bind_host";
    public static final String HTTP_PORT = "http_port";
    public static final String HTTPS_ENABLED = "https_enabled";
    public static final String HTTPS_BIND_HOST = "https_bind_host";
    public static final String HTTPS_PORT = "https_port";
    public static final String HTTPS_SERVER_NAME = "https_server_name";
    public static final String HTTPS_CERTIFICATE_CHAIN = "https_certificate_chain";
    public static final String HTTPS_CERTIFICATE_PATH = "https_certificate_path";
    public static final String HTTPS_PRIVATE_KEY = "https_private_key";
    public static final String HTTPS_PRIVATE_KEY_PATH = "https_private_key_path";
    public static final String SOCKS_ENABLED = "socks_enabled";
    public static final String SOCKS_BIND_HOST = "socks_bind_host";
    public static final String SOCKS_PORT = "socks_port";
    public static final String HTTP_AUTH_REQUIRED = "http_auth_required";
    public static final String SOCKS_AUTH_REQUIRED = "socks_auth_required";
    public static final String SOCKS_UDP_ENABLED = "socks_udp_enabled";
    public static final String BYTES_UP_TOTAL = "bytes_up_total";
    public static final String BYTES_DOWN_TOTAL = "bytes_down_total";

    @Id
    @Column(name = "key", nullable = false, length = 256)
    private String key;

    @Lob
#if SPRING_BOOT_3
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
#else
    @Type(type = "org.hibernate.type.TextType")
#endif
    @Column(name = "value")
    private String value;

    public ConfigEntry(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
