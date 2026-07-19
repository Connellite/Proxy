package io.github.connellite.proxy.client.rpc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.google.gwt.user.client.rpc.IsSerializable;

@Getter
@Setter
@NoArgsConstructor
public class PasswordChangeDto implements IsSerializable {

    private String currentPassword;
    private String newPassword;
    private String confirmPassword;
}
