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
public class SftpPageDto implements IsSerializable {

    private boolean sshEnabled;
    private boolean sshRunning;
    private int sshPort;
    private List<SftpUserRowDto> users = new ArrayList<>();
}
