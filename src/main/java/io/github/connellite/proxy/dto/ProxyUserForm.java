package io.github.connellite.proxy.dto;

#if SPRING_BOOT_3
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
#else
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
#endif
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProxyUserForm {

    private Long id;

    @NotBlank
    @Size(min = 1, max = 64)
    private String username;

    @Size(max = 128)
    private String password;

    private boolean enabled = true;

    @Min(0)
    @Max(10_000)
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
