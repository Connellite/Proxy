package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SshUserKeyRowDto implements IsSerializable {

    private long id;
    private String comment;
    private String fingerprint;
    private String createdAt;
}
