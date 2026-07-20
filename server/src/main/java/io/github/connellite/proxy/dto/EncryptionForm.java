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
public class EncryptionForm {

    private boolean httpsEnabled = false;

    @NotBlank
    @Size(max = 64)
    private String httpsBindHost = "0.0.0.0";

    @Min(1)
    @Max(65535)
    private int httpsPort = 3129;

    @Size(max = 255)
    private String serverName = "";

    private String certificateChain;

    @Size(max = 1024)
    private String certificatePath;

    private String privateKey;

    @Size(max = 1024)
    private String privateKeyPath;

    /** UI-only: existing private key is stored and not echoed back. */
    private boolean privateKeySaved;
}
