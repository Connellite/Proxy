package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminRowDto implements IsSerializable {

    private String username;
    private String updatedAt;
}
