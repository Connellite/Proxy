package io.github.connellite.proxy.client.rpc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.google.gwt.user.client.rpc.IsSerializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UsersPageDto implements IsSerializable {

    private List<AdminRowDto> admins = new ArrayList<>();
    private List<UserRowDto> users = new ArrayList<>();
}
