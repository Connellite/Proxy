package io.github.connellite.proxy.client.rpc.dto;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RelayQueryRowDto implements IsSerializable {

    private String time;
    private String client;
    private String name;
    private String type;
    private String outcome;
    private String answer;
    private String elapsed;
}
