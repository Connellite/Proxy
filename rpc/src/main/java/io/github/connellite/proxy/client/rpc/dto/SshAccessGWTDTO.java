package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SshAccessGWTDTO implements IsSerializable {

    private String userId;
    private boolean sshPasswordEnabled;
    private List<SshUserKeyRowGWTDTO> keys = new ArrayList<SshUserKeyRowGWTDTO>();
}
