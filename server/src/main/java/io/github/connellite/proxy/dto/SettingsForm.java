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
public class SettingsForm {

    private boolean httpEnabled = true;

    @NotBlank
    @Size(max = 64)
    private String httpBindHost = "0.0.0.0";

    @Min(1)
    @Max(65535)
    private int httpPort = 3128;

    private boolean socksEnabled = true;

    @NotBlank
    @Size(max = 64)
    private String socksBindHost = "0.0.0.0";

    @Min(1)
    @Max(65535)
    private int socksPort = 1080;

    private boolean httpAuthRequired = false;

    private boolean socksAuthRequired = false;

    /** Full SOCKS5: TCP CONNECT + UDP ASSOCIATE. Off = TCP CONNECT only. */
    private boolean socksUdpEnabled = false;
}
