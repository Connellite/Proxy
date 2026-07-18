package io.github.connellite.proxy.dto;

#if SPRING_BOOT_3
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
#else
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
#endif
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeForm {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 4, max = 128)
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
