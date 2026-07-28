package io.github.connellite.proxy.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeForm {

    private String currentPassword;

    private String newPassword;

    private String confirmPassword;
}
